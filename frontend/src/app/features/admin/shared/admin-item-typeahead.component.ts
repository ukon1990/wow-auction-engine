import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { AdminApiService, AdminItem1 } from '@api/generated';
import { LocaleService } from '@core/services/locale.service';
import { firstValueFrom } from 'rxjs';

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
                  <span class="truncate">{{ item.effective.name || item.id }}</span>
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
  readonly itemIdChange = output<number | null>();

  protected readonly query = signal('');
  protected readonly results = signal<readonly AdminItem1[]>([]);
  protected readonly open = signal(false);
  protected readonly listboxId = `admin-item-typeahead-${nextTypeaheadId++}`;
  protected readonly noResultsLabel = $localize`:@@admin.recipes.itemTypeahead.empty:No items found`;
  protected readonly rankLabel = $localize`:@@admin.recipes.itemTypeahead.rank:Rank`;

  private readonly api = inject(AdminApiService);
  private readonly locale = inject(LocaleService);
  private searchSequence = 0;
  private selectedId: number | null = null;

  constructor() {
    effect(() => {
      const itemId = this.itemId();
      if (itemId === this.selectedId) return;
      this.selectedId = itemId;
      this.query.set(itemId ? `#${itemId}` : '');
    });
  }

  protected onInput(event: Event): void {
    const query = (event.target as HTMLInputElement).value;
    this.query.set(query);
    this.selectedId = null;
    this.itemIdChange.emit(null);
    const sequence = ++this.searchSequence;
    if (query.trim().length < 2) {
      this.results.set([]);
      this.open.set(false);
      return;
    }
    window.setTimeout(() => void this.search(query, sequence), 200);
  }

  protected select(item: AdminItem1): void {
    this.selectedId = item.id;
    this.query.set(item.effective.name ? `${item.effective.name} (#${item.id})` : `#${item.id}`);
    this.itemIdChange.emit(item.id);
    this.open.set(false);
  }

  private async search(query: string, sequence: number): Promise<void> {
    if (sequence !== this.searchSequence) return;
    const result = await firstValueFrom(
      this.api.searchAdminItems(
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
      ),
    ).catch(() => null);
    if (!result || sequence !== this.searchSequence) return;
    this.results.set(result.items);
    this.open.set(true);
  }
}

let nextTypeaheadId = 0;
