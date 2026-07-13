import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { AdminApiService, NormalizedAuctionHelperProfessionInspection } from '@api/generated';
import { PageFrameComponent } from '@ui';
import {
  AuctionHelperLocalPreview,
  processAuctionHelperFilesInWorker,
} from '../../../utilities/process';

export const MAX_FILE_SIZE_BYTES = 64 * 1024 * 1024;
export const MAX_TOTAL_FILE_SIZE_BYTES = 128 * 1024 * 1024;
const AUCTION_HELPER_FILE = 'auctionhelper.lua';
const PROFESSIONS_FILE = 'auctionhelper_professions.lua';

export type SavedVariablesFiles = {
  auctionHelper: File | null;
  professions: File | null;
  ignoredCount: number;
};

type SavedVariablesSelection =
  | { files: SavedVariablesFiles; error: null }
  | { files: SavedVariablesFiles; error: 'duplicate' | 'fileSize' | 'totalFileSize' };

@Component({
  selector: 'app-profession-talent-trees-page',
  imports: [PageFrameComponent],
  templateUrl: './profession-talent-trees.page.html',
  host: { class: 'flex min-h-0 flex-1 flex-col' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfessionTalentTreesPage {
  private readonly adminApi = inject(AdminApiService);
  private readonly folderInput = viewChild<ElementRef<HTMLInputElement>>('folderInput');
  private readonly auctionHelperInput =
    viewChild<ElementRef<HTMLInputElement>>('auctionHelperInput');
  private readonly professionsInput = viewChild<ElementRef<HTMLInputElement>>('professionsInput');

  protected readonly files = signal<SavedVariablesFiles>(emptySelection());
  protected readonly processing = signal(false);
  protected readonly region = signal('eu');
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly preview = signal<AuctionHelperLocalPreview | null>(null);
  protected readonly result = signal<NormalizedAuctionHelperProfessionInspection | null>(null);

  protected onFolderSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectFiles(input.files ?? []);
  }

  protected onFallbackFileSelected(kind: 'auctionHelper' | 'professions', event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0) ?? null;
    const current = this.files();
    this.selectFiles([
      kind === 'auctionHelper' ? file : current.auctionHelper,
      kind === 'professions' ? file : current.professions,
    ]);
  }

  protected clearSelection(): void {
    const folderInput = this.folderInput();
    const auctionHelperInput = this.auctionHelperInput();
    const professionsInput = this.professionsInput();
    if (folderInput) folderInput.nativeElement.value = '';
    if (auctionHelperInput) auctionHelperInput.nativeElement.value = '';
    if (professionsInput) professionsInput.nativeElement.value = '';
    this.files.set(emptySelection());
    this.error.set(null);
    this.preview.set(null);
    this.result.set(null);
  }

  protected hasSelection(): boolean {
    const files = this.files();
    return files.professions !== null && this.region().length > 0;
  }

  protected onRegionInput(event: Event): void {
    this.region.set((event.target as HTMLInputElement).value.trim().toLowerCase());
    this.preview.set(null);
    this.result.set(null);
  }

  protected async processLocally(): Promise<void> {
    const { professions } = this.files();
    if (!professions) {
      this.error.set(
        $localize`:@@professionTalentTrees.error.fileRequired:Choose a folder containing AuctionHelper_Professions.lua first.`,
      );
      return;
    }

    this.processing.set(true);
    this.error.set(null);
    this.preview.set(null);
    this.result.set(null);
    try {
      this.preview.set(
        await processAuctionHelperFilesInWorker(
          selectedFilesForProcessing(this.files()),
          this.region(),
        ),
      );
    } catch {
      this.error.set(
        $localize`:@@professionTalentTrees.error.inspect:Unable to process these SavedVariables files locally. Check the region and choose a valid AuctionHelper_Professions.lua file, then try again.`,
      );
    } finally {
      this.processing.set(false);
    }
  }

  protected async submitNormalizedData(): Promise<void> {
    const preview = this.preview();
    if (!preview) return;
    this.submitting.set(true);
    this.error.set(null);
    try {
      this.result.set(
        await firstValueFrom(
          this.adminApi.inspectNormalizedAuctionHelperProfessionData(preview.payload),
        ),
      );
    } catch (cause) {
      this.error.set(
        savedVariablesInspectionError(cause) ??
          $localize`:@@professionTalentTrees.error.submit:Unable to submit the normalized profession data. The selected files remain available to retry.`,
      );
    } finally {
      this.submitting.set(false);
    }
  }

  protected fileSize(file: File): string {
    if (file.size < 1024 * 1024) return `${Math.ceil(file.size / 1024)} KiB`;
    return `${(file.size / (1024 * 1024)).toFixed(1)} MiB`;
  }

  protected diagnosticDetail(code: string): string {
    switch (code) {
      case 'CRAFTED_ITEM_MISSING':
        return $localize`:@@professionTalentTrees.diagnostic.craftedItemMissing:One or more item-producing recipes do not include a crafted item ID.`;
      case 'CRAFTING_SKILL_DATA_MISSING':
        return $localize`:@@professionTalentTrees.diagnostic.craftingSkillMissing:One or more quality recipes do not include crafting difficulty or skill data.`;
      case 'TALENT_SCOPE_UNSUPPORTED':
        return $localize`:@@professionTalentTrees.diagnostic.talentScopeUnsupported:The saved export does not contain a supported profession talent scope.`;
      case 'TALENT_EXPORT_INVALID':
        return $localize`:@@professionTalentTrees.diagnostic.talentExportInvalid:The saved profession talent export could not be decoded safely.`;
      default:
        return $localize`:@@professionTalentTrees.diagnostic.generic:Some addon data could not be associated safely and was omitted.`;
    }
  }

  private selectFiles(files: Iterable<File | null>): void {
    const selection = selectSavedVariablesFiles(files);
    this.files.set(selection.files);
    this.preview.set(null);
    this.result.set(null);

    if (selection.error === 'duplicate') {
      this.error.set(
        $localize`:@@professionTalentTrees.error.duplicateFiles:Choose only one copy of each required SavedVariables file.`,
      );
      return;
    }
    if (selection.error === 'fileSize') {
      this.error.set(
        $localize`:@@professionTalentTrees.error.fileSize:Choose files smaller than 64 MiB.`,
      );
      return;
    }
    if (selection.error === 'totalFileSize') {
      this.error.set(
        $localize`:@@professionTalentTrees.error.totalFileSize:Choose SavedVariables files smaller than 128 MiB in total.`,
      );
      return;
    }
    this.error.set(null);
  }
}

function emptySelection(): SavedVariablesFiles {
  return { auctionHelper: null, professions: null, ignoredCount: 0 };
}

export function selectSavedVariablesFiles(files: Iterable<File | null>): SavedVariablesSelection {
  const selected = [...files].filter((file): file is File => file !== null);
  const auctionHelper = selected.filter((file) => file.name.toLowerCase() === AUCTION_HELPER_FILE);
  const professions = selected.filter((file) => file.name.toLowerCase() === PROFESSIONS_FILE);
  const ignoredCount = selected.length - auctionHelper.length - professions.length;

  if (auctionHelper.length > 1 || professions.length > 1) {
    return { files: emptySelection(), error: 'duplicate' };
  }

  const selectedFiles = [auctionHelper[0], professions[0]].filter(
    (file): file is File => file !== undefined,
  );
  if (selectedFiles.some((file) => file.size > MAX_FILE_SIZE_BYTES)) {
    return { files: emptySelection(), error: 'fileSize' };
  }
  if (selectedFiles.reduce((total, file) => total + file.size, 0) > MAX_TOTAL_FILE_SIZE_BYTES) {
    return { files: emptySelection(), error: 'totalFileSize' };
  }

  return {
    files: {
      auctionHelper: auctionHelper[0] ?? null,
      professions: professions[0] ?? null,
      ignoredCount,
    },
    error: null,
  };
}

export function selectedFilesForProcessing(files: SavedVariablesFiles): File[] {
  return [files.auctionHelper, files.professions].filter((file): file is File => file !== null);
}

export function savedVariablesInspectionError(cause: unknown): string | null {
  if (!(cause instanceof HttpErrorResponse)) return null;
  if (cause.status === 0) {
    return $localize`:@@professionTalentTrees.error.apiUnavailable:The local API could not be reached. Start the backend, then try again.`;
  }
  if (!cause.error || typeof cause.error !== 'object') return null;
  const detail = (cause.error as { detail?: unknown }).detail;
  return typeof detail === 'string' && detail.trim() ? detail : null;
}
