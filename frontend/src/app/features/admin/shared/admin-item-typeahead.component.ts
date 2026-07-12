import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AdminApiService, AdminItem1 } from '@api/generated';
import { LocaleService } from '@core/services/locale.service';
import {
  catchError,
  debounce,
  distinctUntilChanged,
  firstValueFrom,
  map,
  of,
  Subject,
  switchMap,
  timer,
} from 'rxjs';

export type AdminItemSelection = {
  readonly id: number;
  readonly name: string | null;
};

@Component({
  selector: 'app-admin-item-typeahead',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  template: `
    <label class="grid gap-1 text-sm font-medium text-on-surface">
      <span>{{ label() }}</span>
      <div class="relative">
        <input
          type="search"
          class="h-10 w-full rounded-md border border-white/10 bg-surface-container px-3 text-on-surface outline-none focus:border-primary"
          role="combobox"
          autocomplete="off"
          [attr.aria-expanded]="open()"
          [attr.aria-controls]="listboxId"
          [attr.placeholder]="placeholder()"
          [value]="query()"
          (input)="onInput($event)"
          (focus)="open.set(results().length > 0)"
          (keydown.escape)="open.set(false)"
        />
        @if (open()) {
          <ul
            class="absolute z-50 mt-1 max-h-64 w-full overflow-auto rounded-md border border-white/10 bg-surface-container-high p-1 shadow-xl"
            role="listbox"
            [id]="listboxId"
          >
            @for (item of results(); track item.id) {
              <li>
                <button
                  type="button"
                  class="flex w-full items-center justify-between gap-3 rounded px-3 py-2 text-left hover:bg-white/5 focus:bg-white/5 focus:outline-none"
                  role="option"
                  (click)="select(item)"
                >
                  <span class="flex min-w-0 items-center gap-2">
                    @if (item.effective.mediaUrl; as mediaUrl) {
                      <img
                        class="h-8 w-8 shrink-0 rounded border border-white/10 object-cover"
                        [src]="mediaUrl"
                        alt=""
                        loading="lazy"
                      />
                    } @else {
                      <span
                        class="h-8 w-8 shrink-0 rounded border border-white/10 bg-surface-container"
                        aria-hidden="true"
                      ></span>
                    }
                    <span class="truncate">{{ item.effective.name || item.id }}</span>
                  </span>
                  <span class="ee-data shrink-0 text-outline">
                    #{{ item.id }}
                    @if (item.effective.rank; as rank) {
                      · {{ rankLabel }} {{ rank }}
                    }
                  </span>
                </button>
              </li>
            } @empty {
              <li class="px-3 py-2 text-sm text-outline">{{ noResultsLabel }}</li>
            }
          </ul>
        }
      </div>
    </label>
  `,
})
export class AdminItemTypeaheadComponent {
  readonly label = input.required<string>();
  readonly placeholder = input('');
  readonly itemId = input<number | null>(null);
  readonly itemName = input<string | null>(null);
  readonly debounceWait = input(200);
  readonly itemChange = output<AdminItemSelection | null>();

  protected readonly query = signal('');
  protected readonly results = signal<readonly AdminItem1[]>([]);
  protected readonly open = signal(false);
  protected readonly listboxId = `admin-item-typeahead-${nextTypeaheadId++}`;
  protected readonly noResultsLabel = $localize`:@@admin.recipes.itemTypeahead.empty:No items found`;
  protected readonly rankLabel = $localize`:@@admin.recipes.itemTypeahead.rank:Rank`;

  private readonly api = inject(AdminApiService);
  private readonly locale = inject(LocaleService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchQueries = new Subject<string>();
  private selectedId: number | null = null;
  private selectedName: string | null = null;

  constructor() {
    this.searchQueries
      .pipe(
        debounce(() => timer(this.debounceWait())),
        distinctUntilChanged(),
        switchMap((query) =>
          this.api
            .searchAdminItems(
              query,
              this.locale.apiLocaleOverride(),
              undefined,
              undefined,
              undefined,
              undefined,
              undefined,
              undefined,
              1,
              20,
            )
            .pipe(
              map((result) => result.items),
              catchError(() => of([] as readonly AdminItem1[])),
            ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((items) => {
        this.results.set(items);
        this.open.set(true);
      });

    effect(() => {
      const itemId = this.itemId();
      const itemName = this.itemName();
      if (itemId === this.selectedId && itemName === this.selectedName) return;
      this.selectedId = itemId;
      this.selectedName = itemName;
      this.query.set(itemName?.trim() || '');
      if (itemId && !itemName?.trim()) void this.resolveItemName(itemId);
    });
  }

  protected onInput(event: Event): void {
    const query = (event.target as HTMLInputElement).value;
    this.query.set(query);
    const normalizedQuery = query.trim();
    if (normalizedQuery.length === 0) {
      this.selectedId = null;
      this.selectedName = null;
      this.itemChange.emit(null);
    }
    if (!this.isSearchable(normalizedQuery)) {
      this.results.set([]);
      this.open.set(false);
      return;
    }
    this.searchQueries.next(normalizedQuery);
  }

  protected select(item: AdminItem1): void {
    this.selectedId = item.id;
    this.selectedName = item.effective.name ?? null;
    this.query.set(this.selectedName || `#${item.id}`);
    this.itemChange.emit({ id: item.id, name: this.selectedName });
    this.open.set(false);
  }

  private isSearchable(query: string): boolean {
    return /^\d+$/.test(query) || query.length >= 2;
  }

  private async resolveItemName(itemId: number): Promise<void> {
    const item = await firstValueFrom(
      this.api.getAdminItem(itemId, this.locale.apiLocaleOverride(), false, false),
    ).catch(() => null);
    if (!item || this.selectedId !== itemId || this.query().trim().length > 0) return;
    this.selectedName = item.effective.name ?? null;
    this.query.set(this.selectedName || `#${itemId}`);
  }
}

let nextTypeaheadId = 0;
