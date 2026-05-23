import { formatNumber } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  LOCALE_ID,
  computed,
  inject,
  input,
  output,
} from '@angular/core';

import { SymbolIconComponent } from '../primitives/symbol-icon.component';

export interface PaginationState {
  readonly page: number;
  readonly pageSize: number;
  readonly totalItems: number;
  readonly totalPages: number;
}

type PaginationItem =
  | {
      readonly type: 'page';
      readonly page: number;
      readonly label: string;
      readonly active: boolean;
    }
  | { readonly type: 'ellipsis'; readonly key: string };

@Component({
  selector: 'ee-pagination',
  host: {
    class: 'block min-w-0 w-full',
  },
  imports: [SymbolIconComponent],
  template: `
    <div class="flex w-full min-w-0 items-center justify-between gap-3">
      <span class="min-w-0 truncate text-left">{{ summary() }}</span>
      <div class="flex min-w-0 items-center justify-end gap-1 overflow-hidden">
        <button
          type="button"
          class="rounded p-1 transition hover:text-primary enabled:cursor-pointer disabled:cursor-not-allowed disabled:opacity-40"
          aria-label="First page"
          [disabled]="!canGoPrevious()"
          (click)="goToFirstPage()"
        >
          <ee-symbol-icon name="keyboard_double_arrow_left" />
        </button>
        <button
          type="button"
          class="rounded p-1 transition hover:text-primary enabled:cursor-pointer disabled:cursor-not-allowed disabled:opacity-40"
          aria-label="Previous page"
          [disabled]="!canGoPrevious()"
          (click)="goToPreviousPage()"
        >
          <ee-symbol-icon name="chevron_left" />
        </button>
        <div class="hidden min-w-0 items-center gap-1 sm:flex">
          @for (item of paginationItems(); track $index) {
            @if (item.type === 'ellipsis') {
              <span aria-hidden="true" class="px-1 text-outline">...</span>
            } @else {
              <button
                type="button"
                class="min-w-8 rounded px-2 py-1 transition enabled:cursor-pointer disabled:cursor-default"
                [class.bg-primary]="item.active"
                [class.text-on-primary]="item.active"
                [class.hover:text-primary]="!item.active"
                [attr.aria-current]="item.active ? 'page' : null"
                [attr.aria-label]="pageLabel(item.page)"
                [disabled]="loading() || item.active"
                (click)="goToPage(item.page)"
              >
                {{ item.label }}
              </button>
            }
          }
        </div>
        <button
          type="button"
          class="rounded p-1 transition hover:text-primary enabled:cursor-pointer disabled:cursor-not-allowed disabled:opacity-40"
          aria-label="Next page"
          [disabled]="!canGoNext()"
          (click)="goToNextPage()"
        >
          <ee-symbol-icon name="chevron_right" />
        </button>
        <button
          type="button"
          class="rounded p-1 transition hover:text-primary enabled:cursor-pointer disabled:cursor-not-allowed disabled:opacity-40"
          aria-label="Last page"
          [disabled]="!canGoNext()"
          (click)="goToLastPage()"
        >
          <ee-symbol-icon name="keyboard_double_arrow_right" />
        </button>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaginationComponent {
  private readonly locale = inject(LOCALE_ID);

  readonly pageState = input<PaginationState | undefined>(undefined);
  readonly loading = input(false);
  readonly loadingSummary = input<string>('Loading rows...');
  readonly emptySummary = input<string>('No rows available.');
  readonly rowLabel = input<string>('rows');
  readonly windowSize = input(5, { transform: Number });

  readonly pageChange = output<number>();

  protected readonly summary = computed(() => {
    if (this.loading()) return this.loadingSummary();
    const state = this.pageState();
    if (!state || state.totalItems === 0) return this.emptySummary();

    const start = state.page * state.pageSize + 1;
    const end = Math.min((state.page + 1) * state.pageSize, state.totalItems);
    const startLabel = this.formatInteger(start);
    const endLabel = this.formatInteger(end);
    const totalLabel = this.formatInteger(state.totalItems);
    return `Showing ${startLabel}-${endLabel} of ${totalLabel} ${this.rowLabel()}`;
  });

  protected readonly canGoPrevious = computed(() => {
    const state = this.pageState();
    return !this.loading() && !!state && state.totalItems > 0 && state.page > 0;
  });

  protected readonly canGoNext = computed(() => {
    const state = this.pageState();
    return !this.loading() && !!state && state.totalItems > 0 && state.page + 1 < state.totalPages;
  });

  protected readonly paginationItems = computed<PaginationItem[]>(() => {
    const state = this.pageState();
    if (!state || state.totalItems === 0 || state.totalPages <= 0) return [];

    const totalPages = state.totalPages;
    const current = state.page;
    const windowSize = Math.max(3, Math.floor(this.windowSize()));
    const interiorSlots = Math.max(1, windowSize - 2);
    const half = Math.floor(interiorSlots / 2);
    let start = Math.max(1, current - half);
    let end = Math.min(totalPages - 2, start + interiorSlots - 1);
    start = Math.max(1, Math.min(start, end - interiorSlots + 1));

    const pages = new Set<number>([0, totalPages - 1]);
    for (let page = start; page <= end; page += 1) pages.add(page);
    if (totalPages <= windowSize) {
      for (let page = 0; page < totalPages; page += 1) pages.add(page);
    }

    const sortedPages = [...pages].sort((a, b) => a - b);
    const items: PaginationItem[] = [];
    for (const page of sortedPages) {
      const previous = items.at(-1);
      if (previous?.type === 'page' && page - previous.page > 1) {
        items.push({ type: 'ellipsis', key: `${previous.page}-${page}` });
      }
      items.push({
        type: 'page',
        page,
        label: String(page + 1),
        active: page === current,
      });
    }
    return items;
  });

  protected goToFirstPage(): void {
    this.emitPage(0);
  }

  protected goToPreviousPage(): void {
    const state = this.pageState();
    if (!state) return;
    this.emitPage(state.page - 1);
  }

  protected goToPage(page: number): void {
    this.emitPage(page);
  }

  protected goToNextPage(): void {
    const state = this.pageState();
    if (!state) return;
    this.emitPage(state.page + 1);
  }

  protected goToLastPage(): void {
    const state = this.pageState();
    if (!state) return;
    this.emitPage(state.totalPages - 1);
  }

  protected pageLabel(page: number): string {
    return `Page ${page + 1}`;
  }

  private emitPage(page: number): void {
    const state = this.pageState();
    if (!state || this.loading()) return;

    const target = Math.max(0, Math.min(page, state.totalPages - 1));
    if (target === state.page) return;
    this.pageChange.emit(target);
  }

  private formatInteger(value: number): string {
    return formatNumber(value, this.locale, '1.0-0');
  }
}
