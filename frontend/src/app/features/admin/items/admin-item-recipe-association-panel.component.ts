import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminItem1, AdminRecipeAssociationRequest, AdminRecipeSearchResult } from '@api/generated';
import { SearchInputComponent, TextInputComponent } from '@ui';

const standaloneModel = { standalone: true };

@Component({
  selector: 'app-admin-item-recipe-association-panel',
  imports: [FormsModule, SearchInputComponent, TextInputComponent],
  template: `
    <div class="grid gap-5">
      @if (item(); as currentItem) {
        <section class="grid gap-2 rounded-md border border-white/10 bg-surface-container p-3">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="ee-label text-outline">Crafted item</p>
              <h3 class="truncate font-semibold text-on-surface">
                {{ currentItem.effective.name || 'Unnamed item' }}
              </h3>
            </div>
            <p class="ee-data text-outline">#{{ currentItem.id }}</p>
          </div>
          @if ((currentItem.effective.recipes ?? []).length > 0) {
            <div class="grid gap-2">
              @for (recipe of currentItem.effective.recipes ?? []; track recipe.recipeId) {
                <div
                  class="flex flex-wrap items-center justify-between gap-2 rounded-md border border-white/10 bg-surface-container-highest px-3 py-2"
                >
                  <div class="min-w-0">
                    <p class="truncate text-sm font-semibold text-on-surface">{{ recipe.name }}</p>
                    <p class="ee-data text-outline">{{ recipe.professionName }}</p>
                  </div>
                  <button
                    type="button"
                    class="rounded-md border border-white/10 px-3 py-2 text-sm font-semibold text-on-surface transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container disabled:cursor-wait disabled:opacity-60"
                    [disabled]="submitting()"
                    (click)="clearRecipe(recipe.recipeId)"
                  >
                    Clear
                  </button>
                </div>
              }
            </div>
          } @else {
            <p class="ee-data text-outline">No recipes are associated with this item.</p>
          }
        </section>

        <form class="grid gap-4" (submit)="submit($event)">
          @if (submitError()) {
            <p
              class="rounded-md border border-error/30 bg-error/10 px-3 py-2 text-sm text-error"
              role="alert"
            >
              {{ submitError() }}
            </p>
          }

          <ee-search-input
            label="Recipe"
            [showLabel]="true"
            placeholder="Search by recipe name or ID"
            [ngModel]="query()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="setQuery($event)"
          />

          @if (searching()) {
            <p class="ee-data text-outline" role="status">Searching recipes...</p>
          } @else if (results().length > 0) {
            <div class="grid max-h-80 gap-2 overflow-auto pr-1" role="listbox" aria-label="Recipes">
              @for (recipe of results(); track recipe.recipeId) {
                <button
                  type="button"
                  class="grid gap-1 rounded-md border px-3 py-2 text-left transition focus:outline-none focus:ring-2 focus:ring-primary-container"
                  [class.border-primary]="selectedRecipe()?.recipeId === recipe.recipeId"
                  [class.bg-primary-container]="selectedRecipe()?.recipeId === recipe.recipeId"
                  [class.text-on-primary-container]="selectedRecipe()?.recipeId === recipe.recipeId"
                  [class.border-white\\/10]="selectedRecipe()?.recipeId !== recipe.recipeId"
                  [class.bg-surface-container]="selectedRecipe()?.recipeId !== recipe.recipeId"
                  (click)="selectRecipe(recipe)"
                >
                  <span class="text-sm font-semibold">{{ recipe.name }}</span>
                  <span class="ee-data">
                    {{ recipe.professionName }} · {{ recipe.skillTierName }} ·
                    {{ recipe.professionCategoryName }}
                  </span>
                  <span
                    class="ee-data"
                    [class.text-outline]="selectedRecipe()?.recipeId !== recipe.recipeId"
                  >
                    @if (recipe.craftedItemId) {
                      Associated with #{{ recipe.craftedItemId }}
                      {{ recipe.craftedItemName || '' }}
                      @if (recipe.craftedQuantity) {
                        · qty {{ recipe.craftedQuantity }}
                      }
                    } @else {
                      Not associated with a crafted item
                    }
                  </span>
                </button>
              }
            </div>
          } @else if (query().trim().length >= 2) {
            <p class="ee-data text-outline">No recipes match this search.</p>
          }

          <ee-text-input
            label="Crafted quantity"
            type="number"
            [disabled]="!selectedRecipe() || submitting()"
            [ngModel]="craftedQuantity()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="craftedQuantity.set($event)"
          />

          <div class="flex flex-wrap justify-end gap-2">
            <button
              type="button"
              class="rounded-md border border-white/10 px-4 py-2 font-semibold text-on-surface transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container"
              (click)="cancelled.emit()"
            >
              Close
            </button>
            <button
              type="submit"
              class="rounded-md bg-primary px-4 py-2 font-semibold text-on-primary transition hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-primary-container disabled:cursor-wait disabled:opacity-70"
              [disabled]="!selectedRecipe() || submitting()"
            >
              Associate recipe
            </button>
          </div>
        </form>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminItemRecipeAssociationPanelComponent {
  readonly item = input.required<AdminItem1>();
  readonly results = input<readonly AdminRecipeSearchResult[]>([]);
  readonly searching = input(false);
  readonly submitting = input(false);
  readonly submitError = input<string | null>(null);
  readonly search = output<string>();
  readonly submitted = output<{ recipeId: number; request: AdminRecipeAssociationRequest }>();
  readonly cancelled = output<void>();

  protected readonly query = signal('');
  protected readonly selectedRecipe = signal<AdminRecipeSearchResult | null>(null);
  protected readonly craftedQuantity = signal('1');
  protected readonly standaloneModel = standaloneModel;

  protected setQuery(value: string): void {
    this.query.set(value);
    this.selectedRecipe.set(null);
    this.search.emit(value);
  }

  protected selectRecipe(recipe: AdminRecipeSearchResult): void {
    this.selectedRecipe.set(recipe);
    this.craftedQuantity.set(String(recipe.craftedQuantity ?? 1));
  }

  protected clearRecipe(recipeId: number): void {
    this.submitted.emit({
      recipeId,
      request: {
        craftedItemId: null,
        craftedQuantity: null,
      },
    });
  }

  protected submit(event: Event): void {
    event.preventDefault();
    const recipe = this.selectedRecipe();
    if (!recipe) return;
    const quantity = Number.parseInt(this.craftedQuantity(), 10);
    this.submitted.emit({
      recipeId: recipe.recipeId,
      request: {
        craftedItemId: this.item().id,
        craftedQuantity: Number.isFinite(quantity) ? quantity : 1,
      },
    });
  }
}
