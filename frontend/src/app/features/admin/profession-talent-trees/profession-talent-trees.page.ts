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
import { ToastService } from '@core/services/toast.service';
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

export type ProfessionRecipeOverview = Readonly<{
  professionId: number;
  name: string;
  characterCount: number;
  recipeCount: number;
  recipes: readonly Readonly<{
    recipeId: number;
    name: string;
    craftedItemId?: number;
    reagentSlotCount: number;
    baseDifficulty?: number;
  }>[];
}>;

@Component({
  selector: 'app-profession-talent-trees-page',
  imports: [PageFrameComponent],
  templateUrl: './profession-talent-trees.page.html',
  host: { class: 'flex min-h-0 flex-1 flex-col' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfessionTalentTreesPage {
  private readonly adminApi = inject(AdminApiService);
  private readonly toast = inject(ToastService);
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
    if (this.selectFiles(input.files ?? [])) void this.processLocally();
  }

  protected onFallbackFileSelected(kind: 'auctionHelper' | 'professions', event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0) ?? null;
    const current = this.files();
    if (
      this.selectFiles([
        kind === 'auctionHelper' ? file : current.auctionHelper,
        kind === 'professions' ? file : current.professions,
      ])
    )
      void this.processLocally();
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
    return canProcessSelection(this.files(), this.region());
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
      this.toast.success(
        $localize`:@@professionTalentTrees.success.submit:Normalized profession data was accepted by the server.`,
      );
    } catch (cause) {
      this.toast.error(
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

  protected professionOverview(preview: AuctionHelperLocalPreview): ProfessionRecipeOverview[] {
    return buildProfessionRecipeOverview(preview);
  }

  private selectFiles(files: Iterable<File | null>): boolean {
    const selection = selectSavedVariablesFiles(files);
    this.files.set(selection.files);
    this.preview.set(null);
    this.result.set(null);

    if (selection.error === 'duplicate') {
      this.error.set(
        $localize`:@@professionTalentTrees.error.duplicateFiles:Choose only one copy of each required SavedVariables file.`,
      );
      return false;
    }
    if (selection.error === 'fileSize') {
      this.error.set(
        $localize`:@@professionTalentTrees.error.fileSize:Choose files smaller than 64 MiB.`,
      );
      return false;
    }
    if (selection.error === 'totalFileSize') {
      this.error.set(
        $localize`:@@professionTalentTrees.error.totalFileSize:Choose SavedVariables files smaller than 128 MiB in total.`,
      );
      return false;
    }
    this.error.set(null);
    return canProcessSelection(selection.files, this.region());
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

export function canProcessSelection(files: SavedVariablesFiles, region: string): boolean {
  return files.professions !== null && region.trim().length > 0;
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

export function buildProfessionRecipeOverview(
  preview: AuctionHelperLocalPreview,
): ProfessionRecipeOverview[] {
  const professions = new Map<
    number,
    {
      name: string;
      characters: Set<string>;
      recipes: Map<number, ProfessionRecipeOverview['recipes'][number]>;
    }
  >();
  for (const character of preview.payload.characters) {
    for (const profession of character.professions) {
      const aggregate = professions.get(profession.professionId) ?? {
        name: profession.name,
        characters: new Set<string>(),
        recipes: new Map(),
      };
      aggregate.characters.add(character.characterKey);
      for (const recipe of profession.recipes) {
        if (!aggregate.recipes.has(recipe.recipeId)) {
          aggregate.recipes.set(recipe.recipeId, {
            recipeId: recipe.recipeId,
            name: recipe.name,
            ...(recipe.craftedItemId !== undefined ? { craftedItemId: recipe.craftedItemId } : {}),
            reagentSlotCount: recipe.reagentSlots.length,
            ...(recipe.baseDifficulty !== undefined
              ? { baseDifficulty: recipe.baseDifficulty }
              : {}),
          });
        }
      }
      professions.set(profession.professionId, aggregate);
    }
  }
  return [...professions.entries()]
    .map(([professionId, profession]) => ({
      professionId,
      name: profession.name,
      characterCount: profession.characters.size,
      recipeCount: profession.recipes.size,
      recipes: [...profession.recipes.values()].sort((a, b) => a.name.localeCompare(b.name)),
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
}
