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

import { AdminApiService, AuctionHelperSavedVariablesInspection } from '@api/generated';
import { PageFrameComponent } from '@ui';

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
  protected readonly importing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<AuctionHelperSavedVariablesInspection | null>(null);

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
    this.result.set(null);
  }

  protected hasSelection(): boolean {
    const files = this.files();
    return files.auctionHelper !== null || files.professions !== null;
  }

  protected async inspectUpload(): Promise<void> {
    const { auctionHelper, professions } = this.files();
    if (!auctionHelper && !professions) {
      this.error.set(
        $localize`:@@professionTalentTrees.error.fileRequired:Choose the SavedVariables folder or at least one recognized file first.`,
      );
      return;
    }

    this.importing.set(true);
    this.error.set(null);
    this.result.set(null);
    try {
      const result = await firstValueFrom(
        this.adminApi.inspectAuctionHelperSavedVariables(selectedFilesForUpload(this.files())),
      );
      this.result.set(result);
    } catch (cause) {
      const result = savedVariablesInspection(cause);
      if (result) {
        this.result.set(result);
      } else {
        this.error.set(
          $localize`:@@professionTalentTrees.error.inspect:Unable to inspect these SavedVariables files. Try again with AuctionHelper.lua and AuctionHelper_Professions.lua.`,
        );
      }
    } finally {
      this.importing.set(false);
    }
  }

  protected fileSize(file: File): string {
    if (file.size < 1024 * 1024) return `${Math.ceil(file.size / 1024)} KiB`;
    return `${(file.size / (1024 * 1024)).toFixed(1)} MiB`;
  }

  private selectFiles(files: Iterable<File | null>): void {
    const selection = selectSavedVariablesFiles(files);
    this.files.set(selection.files);
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

export function selectedFilesForUpload(files: SavedVariablesFiles): File[] {
  return [files.auctionHelper, files.professions].filter((file): file is File => file !== null);
}

function savedVariablesInspection(cause: unknown): AuctionHelperSavedVariablesInspection | null {
  if (!(cause instanceof HttpErrorResponse) || !cause.error || typeof cause.error !== 'object') {
    return null;
  }
  const candidate = cause.error as Partial<AuctionHelperSavedVariablesInspection>;
  return Array.isArray(candidate.diagnostics) && Array.isArray(candidate.sources)
    ? (candidate as AuctionHelperSavedVariablesInspection)
    : null;
}
