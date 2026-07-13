import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { AdminApiService, AuctionHelperTalentTreeLuaImportResult } from '@api/generated';
import { PageFrameComponent } from '@ui';

const MAX_FILE_SIZE_BYTES = 64 * 1024 * 1024;

@Component({
  selector: 'app-profession-talent-trees-page',
  imports: [PageFrameComponent],
  templateUrl: './profession-talent-trees.page.html',
  host: { class: 'flex min-h-0 flex-1 flex-col' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfessionTalentTreesPage {
  private readonly adminApi = inject(AdminApiService);

  protected readonly file = signal<File | null>(null);
  protected readonly importing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<AuctionHelperTalentTreeLuaImportResult | null>(null);

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0) ?? null;
    this.result.set(null);
    if (!file) {
      this.file.set(null);
      return;
    }
    if (!file.name.toLowerCase().endsWith('.lua')) {
      this.file.set(null);
      this.error.set(
        $localize`:@@professionTalentTrees.error.fileType:Choose an AuctionHelper .lua file.`,
      );
      return;
    }
    if (file.size > MAX_FILE_SIZE_BYTES) {
      this.file.set(null);
      this.error.set(
        $localize`:@@professionTalentTrees.error.fileSize:Choose a file smaller than 64 MiB.`,
      );
      return;
    }
    this.error.set(null);
    this.file.set(file);
  }

  protected async inspectUpload(): Promise<void> {
    const file = this.file();
    if (!file) {
      this.error.set(
        $localize`:@@professionTalentTrees.error.fileRequired:Choose a Lua file first.`,
      );
      return;
    }

    this.importing.set(true);
    this.error.set(null);
    this.result.set(null);
    try {
      const result = await firstValueFrom(this.adminApi.inspectProfessionTalentTreeLua(file));
      this.result.set(result as AuctionHelperTalentTreeLuaImportResult);
    } catch (cause) {
      const result = luaImportResult(cause);
      if (result) {
        this.result.set(result);
      } else {
        this.error.set(
          $localize`:@@professionTalentTrees.error.inspect:Unable to inspect this Lua file. Try again with AuctionHelper_Professions.lua.`,
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
}

function luaImportResult(cause: unknown): AuctionHelperTalentTreeLuaImportResult | null {
  if (!(cause instanceof HttpErrorResponse) || !cause.error || typeof cause.error !== 'object') {
    return null;
  }
  const candidate = cause.error as Partial<AuctionHelperTalentTreeLuaImportResult>;
  return Array.isArray(candidate.diagnostics) && typeof candidate.contentHash === 'string'
    ? (candidate as AuctionHelperTalentTreeLuaImportResult)
    : null;
}
