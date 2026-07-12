import { ChangeDetectionStrategy, Component, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AdminRecipe1,
  AdminRecipeOutput,
  AdminRecipeOverrideRequest,
  AdminRecipeReagent,
  AdminRecipeReagentRank,
} from '@api/generated';
import {
  AdminItemSelection,
  AdminItemTypeaheadComponent,
} from '@features/admin/shared/admin-item-typeahead.component';

const standaloneModel = { standalone: true };
const REAGENT_RANKS = [1, 2, 3] as const;

@Component({
  selector: 'app-admin-recipe-override-form',
  imports: [FormsModule, AdminItemTypeaheadComponent],
  template: `
    <form class="grid gap-6" (submit)="onSubmit($event)">
      @if (recipe(); as currentRecipe) {
        <header class="grid gap-1">
          <p class="ee-label text-outline">#{{ currentRecipe.id }}</p>
          <h3 class="font-cinzel text-xl font-bold text-on-surface">
            {{ recipeTitle(currentRecipe) }}
          </h3>
          <p class="ee-data text-outline">
            {{ currentRecipe.effective.professionName }} ·
            {{ currentRecipe.effective.skillTierName }} ·
            {{ currentRecipe.effective.professionCategoryName }}
          </p>
        </header>

        <section class="grid gap-3" [attr.aria-label]="scalarSectionLabel">
          <h4 class="font-semibold text-primary-container">{{ scalarSectionLabel }}</h4>
          <div class="grid gap-3 md:grid-cols-2">
            <app-admin-item-typeahead
              [label]="craftedItemIdLabel"
              [placeholder]="itemSearchPlaceholder"
              [itemId]="craftedItemId()"
              (itemChange)="selectCraftedItem($event)"
            />
            <label class="admin-field">
              <span>{{ craftedQuantityLabel }}</span>
              <input
                type="number"
                min="1"
                [ngModel]="craftedQuantity()"
                [ngModelOptions]="standaloneModel"
                (ngModelChange)="craftedQuantity.set($event)"
              />
            </label>
            <label class="admin-field">
              <span>{{ rankLabel }}</span>
              <input
                type="number"
                min="0"
                [ngModel]="rank()"
                [ngModelOptions]="standaloneModel"
                (ngModelChange)="rank.set($event)"
              />
            </label>
            <label class="admin-field">
              <span>{{ requiredSkillLevelLabel }}</span>
              <input
                type="number"
                min="0"
                [ngModel]="requiredSkillLevel()"
                [ngModelOptions]="standaloneModel"
                (ngModelChange)="requiredSkillLevel.set($event)"
              />
            </label>
          </div>
          <label class="admin-field">
            <span>{{ overrideNoteLabel }}</span>
            <textarea
              rows="3"
              [ngModel]="overrideNote()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="overrideNote.set($event)"
            ></textarea>
          </label>
        </section>

        <section class="grid gap-3" [attr.aria-label]="outputsSectionLabel">
          <div class="flex items-center justify-between gap-3">
            <h4 class="font-semibold text-primary-container">{{ outputsSectionLabel }}</h4>
            <button type="button" class="admin-secondary-button" (click)="addOutput()">
              {{ addOutputLabel }}
            </button>
          </div>
          @for (output of outputs(); track $index) {
            <div class="admin-row-grid">
              <app-admin-item-typeahead
                [label]="outputItemIdLabel"
                [placeholder]="itemSearchPlaceholder"
                [itemId]="output.craftedItemId"
                [itemName]="output.craftedItemName ?? null"
                (itemChange)="selectOutputItem($index, $event)"
              />
              <label class="admin-field">
                <span>{{ outputQuantityLabel }}</span>
                <input
                  type="number"
                  min="1"
                  [ngModel]="output.craftedQuantity"
                  [ngModelOptions]="standaloneModel"
                  (ngModelChange)="updateOutput($index, { craftedQuantity: $event })"
                />
              </label>
              <label class="admin-field">
                <span>{{ outputSkillLabel }}</span>
                <input
                  type="number"
                  min="0"
                  [ngModel]="output.requiredSkillLevel"
                  [ngModelOptions]="standaloneModel"
                  (ngModelChange)="updateOutput($index, { requiredSkillLevel: $event })"
                />
              </label>
              <button
                type="button"
                class="admin-secondary-button self-end"
                [attr.aria-label]="removeOutputAriaLabel($index)"
                (click)="removeOutput($index)"
              >
                {{ removeLabel }}
              </button>
            </div>
          } @empty {
            <p class="ee-data text-outline">{{ noOutputsLabel }}</p>
          }
        </section>

        <section class="grid gap-3" [attr.aria-label]="reagentsSectionLabel">
          <div class="flex items-center justify-between gap-3">
            <h4 class="font-semibold text-primary-container">{{ reagentsSectionLabel }}</h4>
            <button type="button" class="admin-secondary-button" (click)="addReagent()">
              {{ addReagentLabel }}
            </button>
          </div>
          @for (reagent of reagents(); track $index) {
            <div class="admin-row-grid">
              <app-admin-item-typeahead
                [label]="reagentItemIdLabel"
                [placeholder]="itemSearchPlaceholder"
                [itemId]="reagent.itemId"
                [itemName]="reagent.itemName ?? null"
                (itemChange)="selectReagentItem($index, $event)"
              />
              <label class="admin-field">
                <span>{{ reagentQuantityLabel }}</span>
                <input
                  type="number"
                  min="1"
                  [ngModel]="reagent.quantity"
                  [ngModelOptions]="standaloneModel"
                  (ngModelChange)="updateReagent($index, { quantity: $event })"
                />
              </label>
              <label class="admin-field">
                <span>{{ sortOrderLabel }}</span>
                <input
                  type="number"
                  min="0"
                  [ngModel]="reagent.sortOrder"
                  [ngModelOptions]="standaloneModel"
                  (ngModelChange)="updateReagent($index, { sortOrder: $event })"
                />
              </label>
              <button
                type="button"
                class="admin-secondary-button self-end"
                [attr.aria-label]="removeReagentAriaLabel($index)"
                (click)="removeReagent($index)"
              >
                {{ removeLabel }}
              </button>

              <div class="admin-rank-grid md:col-span-full">
                <button
                  type="button"
                  class="justify-self-start text-left font-semibold text-primary-container underline-offset-4 transition hover:underline focus:outline-none focus:ring-2 focus:ring-primary-container"
                  [attr.aria-expanded]="isReagentExpanded($index)"
                  [attr.aria-label]="rankAlternativesAriaLabel($index)"
                  (click)="toggleReagentRanks($index)"
                >
                  {{ rankAlternativesLabel }}
                </button>
                @if (isReagentExpanded($index)) {
                  @for (rankValue of reagentRanks; track rankValue) {
                    <div class="admin-rank-row">
                      <p class="ee-label self-center text-outline">
                        {{ rankNumberLabel(rankValue) }}
                      </p>
                      <app-admin-item-typeahead
                        [label]="rankItemIdLabel"
                        [placeholder]="itemSearchPlaceholder"
                        [itemId]="rankItemId($index, rankValue)"
                        (itemChange)="selectRankItem($index, rankValue, $event)"
                      />
                      <label class="admin-field">
                        <span>{{ skillPointsLabel }}</span>
                        <input
                          type="number"
                          min="0"
                          [ngModel]="rankField($index, rankValue, 'skillPoints')"
                          [ngModelOptions]="standaloneModel"
                          (ngModelChange)="
                            updateReagentRank($index, rankValue, { skillPoints: $event })
                          "
                        />
                      </label>
                    </div>
                  }
                }
              </div>
            </div>
          } @empty {
            <p class="ee-data text-outline">{{ noReagentsLabel }}</p>
          }
        </section>

        @if (submitError()) {
          <p
            class="rounded-md border border-error/30 bg-error/10 px-3 py-2 text-sm text-error"
            role="alert"
          >
            {{ submitError() }}
          </p>
        }

        <div class="flex flex-wrap justify-end gap-3">
          <button type="button" class="admin-secondary-button" (click)="cancelled.emit()">
            {{ cancelLabel }}
          </button>
          <button type="submit" class="admin-primary-button" [disabled]="submitting()">
            {{ saveLabel }}
          </button>
        </div>
      }
    </form>
  `,
  styleUrl: './admin-recipe-override-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminRecipeOverrideFormComponent {
  protected readonly standaloneModel = standaloneModel;
  protected readonly itemSearchPlaceholder = $localize`:@@admin.recipes.form.itemSearchPlaceholder:Search items by name or ID`;
  protected readonly reagentRanks = REAGENT_RANKS;

  readonly recipe = input.required<AdminRecipe1>();
  readonly submitting = input(false);
  readonly submitError = input<string | null>(null);
  readonly submitted = output<AdminRecipeOverrideRequest>();
  readonly cancelled = output<void>();

  protected readonly scalarSectionLabel = $localize`:@@admin.recipes.form.scalarOverrides:Scalar overrides`;
  protected readonly craftedItemIdLabel = $localize`:@@admin.recipes.form.craftedItemId:Crafted item ID`;
  protected readonly craftedQuantityLabel = $localize`:@@admin.recipes.form.craftedQuantity:Crafted quantity`;
  protected readonly rankLabel = $localize`:@@admin.recipes.form.rank:Rank`;
  protected readonly requiredSkillLevelLabel = $localize`:@@admin.recipes.form.requiredSkillLevel:Required skill level`;
  protected readonly overrideNoteLabel = $localize`:@@admin.recipes.form.overrideNote:Override note`;
  protected readonly outputsSectionLabel = $localize`:@@admin.recipes.form.outputs:Crafted outputs`;
  protected readonly addOutputLabel = $localize`:@@admin.recipes.form.addOutput:Add output`;
  protected readonly outputItemIdLabel = $localize`:@@admin.recipes.form.outputItemId:Item ID`;
  protected readonly outputQuantityLabel = $localize`:@@admin.recipes.form.outputQuantity:Quantity`;
  protected readonly outputSkillLabel = $localize`:@@admin.recipes.form.outputSkill:Skill`;
  protected readonly removeLabel = $localize`:@@admin.recipes.form.remove:Remove`;
  protected readonly noOutputsLabel = $localize`:@@admin.recipes.form.noOutputs:No output overrides. The base crafted item is inherited.`;
  protected readonly reagentsSectionLabel = $localize`:@@admin.recipes.form.reagents:Reagents`;
  protected readonly addReagentLabel = $localize`:@@admin.recipes.form.addReagent:Add reagent`;
  protected readonly reagentItemIdLabel = $localize`:@@admin.recipes.form.reagentItemId:Item ID`;
  protected readonly reagentQuantityLabel = $localize`:@@admin.recipes.form.reagentQuantity:Quantity`;
  protected readonly sortOrderLabel = $localize`:@@admin.recipes.form.sortOrder:Sort order`;
  protected readonly noReagentsLabel = $localize`:@@admin.recipes.form.noReagents:No reagent overrides. Base reagents are inherited.`;
  protected readonly rankAlternativesLabel = $localize`:@@admin.recipes.form.rankAlternatives:Rank alternatives (1–3)`;
  protected readonly rankItemIdLabel = $localize`:@@admin.recipes.form.rankItemId:Item ID`;
  protected readonly skillPointsLabel = $localize`:@@admin.recipes.form.skillPoints:Skill points`;
  protected readonly saveLabel = $localize`:@@admin.recipes.form.save:Save override`;
  protected readonly cancelLabel = $localize`:@@admin.recipes.form.cancel:Cancel`;

  protected readonly craftedItemId = signal<number | null>(null);
  protected readonly craftedQuantity = signal<number | null>(null);
  protected readonly rank = signal<number | null>(null);
  protected readonly requiredSkillLevel = signal<number | null>(null);
  protected readonly overrideNote = signal('');
  protected readonly outputs = signal<AdminRecipeOutput[]>([]);
  protected readonly reagents = signal<AdminRecipeReagent[]>([]);
  protected readonly expandedReagentIndexes = signal<ReadonlySet<number>>(new Set());

  constructor() {
    effect(() => {
      this.initialize(this.recipe());
    });
  }

  protected recipeTitle(recipe: AdminRecipe1): string {
    return recipe.effective.name ?? $localize`:@@admin.recipes.unnamed:Unnamed recipe`;
  }

  protected rankNumberLabel(rank: number): string {
    return $localize`:@@admin.recipes.form.rankNumber:Rank ${rank}:INTERPOLATION:`;
  }

  protected removeOutputAriaLabel(index: number): string {
    return $localize`:@@admin.recipes.form.removeOutput:Remove output ${index + 1}:INTERPOLATION:`;
  }

  protected removeReagentAriaLabel(index: number): string {
    return $localize`:@@admin.recipes.form.removeReagent:Remove reagent ${index + 1}:INTERPOLATION:`;
  }

  protected rankAlternativesAriaLabel(index: number): string {
    return $localize`:@@admin.recipes.form.rankAlternativesFor:Rank alternatives for reagent ${index + 1}:INTERPOLATION:`;
  }

  protected isReagentExpanded(index: number): boolean {
    return this.expandedReagentIndexes().has(index);
  }

  protected toggleReagentRanks(index: number): void {
    this.expandedReagentIndexes.update((current) => {
      const next = new Set(current);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  }

  protected addOutput(): void {
    this.outputs.update((outputs) => [
      ...outputs,
      { craftedItemId: 0, craftedQuantity: 1, sortOrder: outputs.length },
    ]);
  }

  protected selectCraftedItem(selection: AdminItemSelection | null): void {
    this.craftedItemId.set(selection?.id ?? null);
  }

  protected selectOutputItem(index: number, selection: AdminItemSelection | null): void {
    this.updateOutput(index, {
      craftedItemId: selection?.id ?? 0,
      craftedItemName: selection?.name ?? null,
    });
  }

  protected updateOutput(index: number, patch: Partial<AdminRecipeOutput>): void {
    this.outputs.update((outputs) =>
      outputs.map((output, outputIndex) =>
        outputIndex === index ? { ...output, ...patch } : output,
      ),
    );
  }

  protected removeOutput(index: number): void {
    this.outputs.update((outputs) => outputs.filter((_, outputIndex) => outputIndex !== index));
  }

  protected addReagent(): void {
    this.reagents.update((reagents) => [
      ...reagents,
      { itemId: 0, quantity: 1, sortOrder: reagents.length, ranks: [] },
    ]);
  }

  protected selectReagentItem(index: number, selection: AdminItemSelection | null): void {
    this.updateReagent(index, {
      itemId: selection?.id ?? 0,
      itemName: selection?.name ?? null,
    });
  }

  protected selectRankItem(
    reagentIndex: number,
    rank: number,
    selection: AdminItemSelection | null,
  ): void {
    this.updateReagentRank(reagentIndex, rank, { itemId: selection?.id ?? 0 });
  }

  protected updateReagent(index: number, patch: Partial<AdminRecipeReagent>): void {
    this.reagents.update((reagents) =>
      reagents.map((reagent, reagentIndex) =>
        reagentIndex === index ? { ...reagent, ...patch } : reagent,
      ),
    );
  }

  protected removeReagent(index: number): void {
    this.reagents.update((reagents) =>
      reagents.filter((_, reagentIndex) => reagentIndex !== index),
    );
    this.expandedReagentIndexes.update((current) => {
      const next = new Set<number>();
      for (const expandedIndex of current) {
        if (expandedIndex < index) {
          next.add(expandedIndex);
        } else if (expandedIndex > index) {
          next.add(expandedIndex - 1);
        }
      }
      return next;
    });
  }

  protected rankField(
    reagentIndex: number,
    rank: number,
    field: 'itemId' | 'skillPoints',
  ): number | string | null {
    const entry = this.reagents()[reagentIndex]?.ranks?.find((value) => value.rank === rank);
    if (!entry) {
      return field === 'itemId' ? 0 : '';
    }
    if (field === 'itemId') {
      return entry.itemId;
    }
    return entry.skillPoints ?? '';
  }

  protected rankItemId(reagentIndex: number, rank: number): number | null {
    const value = this.rankField(reagentIndex, rank, 'itemId');
    return typeof value === 'number' && value > 0 ? value : null;
  }

  protected updateReagentRank(
    reagentIndex: number,
    rank: number,
    patch: Partial<{ itemId: number; skillPoints: number | string | null }>,
  ): void {
    this.reagents.update((reagents) =>
      reagents.map((reagent, index) => {
        if (index !== reagentIndex) return reagent;

        const ranks = [...(reagent.ranks ?? [])];
        const existingIndex = ranks.findIndex((entry) => entry.rank === rank);
        const current: AdminRecipeReagentRank =
          existingIndex >= 0 ? ranks[existingIndex] : { rank, itemId: 0, skillPoints: null };

        const nextItemId = patch.itemId !== undefined ? Number(patch.itemId) : current.itemId;
        const nextSkillPoints =
          patch.skillPoints === undefined
            ? current.skillPoints
            : patch.skillPoints === null || patch.skillPoints === ''
              ? null
              : Number(patch.skillPoints);

        if (!Number.isFinite(nextItemId) || nextItemId <= 0) {
          if (existingIndex >= 0) {
            ranks.splice(existingIndex, 1);
          }
        } else if (existingIndex >= 0) {
          ranks[existingIndex] = {
            rank,
            itemId: nextItemId,
            skillPoints: Number.isFinite(nextSkillPoints ?? NaN) ? nextSkillPoints : null,
          };
        } else {
          ranks.push({
            rank,
            itemId: nextItemId,
            skillPoints: Number.isFinite(nextSkillPoints ?? NaN) ? nextSkillPoints : null,
          });
        }

        return {
          ...reagent,
          ranks: ranks.sort((left, right) => left.rank - right.rank),
        };
      }),
    );
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    this.submitted.emit(normalizeRequest(this.buildRequest()));
  }

  private buildRequest(): AdminRecipeOverrideRequest {
    return {
      craftedItemId: this.craftedItemId(),
      craftedQuantity: this.craftedQuantity(),
      rank: this.rank(),
      requiredSkillLevel: this.requiredSkillLevel(),
      overrideNote: this.overrideNote().trim() || null,
      outputs: this.outputs().map((output, index) => ({
        craftedItemId: output.craftedItemId,
        craftedQuantity: output.craftedQuantity,
        ...(output.requiredSkillLevel == null
          ? {}
          : { requiredSkillLevel: output.requiredSkillLevel }),
        sortOrder: output.sortOrder ?? index,
      })),
      reagents: this.reagents().map((reagent, index) => ({
        itemId: reagent.itemId,
        quantity: reagent.quantity,
        sortOrder: reagent.sortOrder ?? index,
        ranks: (reagent.ranks ?? [])
          .filter((entry) => entry.itemId > 0)
          .map((entry) => ({
            rank: entry.rank,
            itemId: entry.itemId,
            skillPoints: entry.skillPoints,
          })),
      })),
    };
  }

  private initialize(recipe: AdminRecipe1): void {
    const override = recipe.override ?? {};
    const sourceOutputs = override.outputs ?? recipe.effective.outputs ?? [];
    const sourceReagents = override.reagents ?? recipe.effective.reagents ?? [];

    this.craftedItemId.set(override.craftedItemId ?? null);
    this.craftedQuantity.set(override.craftedQuantity ?? null);
    this.rank.set(override.rank ?? null);
    this.requiredSkillLevel.set(override.requiredSkillLevel ?? null);
    this.overrideNote.set(override.overrideNote ?? '');
    this.outputs.set([...sourceOutputs]);
    this.reagents.set(
      sourceReagents.map((reagent) => ({
        ...reagent,
        ranks: [...(reagent.ranks ?? [])],
      })),
    );
    this.expandedReagentIndexes.set(new Set());
  }
}

export function normalizeRequest(request: AdminRecipeOverrideRequest): AdminRecipeOverrideRequest {
  return {
    craftedItemId: toNullableInt(request.craftedItemId),
    craftedQuantity: toNullableInt(request.craftedQuantity),
    rank: toNullableInt(request.rank),
    requiredSkillLevel: toNullableInt(request.requiredSkillLevel),
    overrideNote: request.overrideNote?.trim() || null,
    outputs: request.outputs
      ?.filter((output) => output.craftedItemId > 0 && output.craftedQuantity > 0)
      .map((output) => ({
        craftedItemId: output.craftedItemId,
        craftedQuantity: output.craftedQuantity,
        requiredSkillLevel: output.requiredSkillLevel,
        sortOrder: output.sortOrder,
      })),
    reagents: request.reagents
      ?.filter((reagent) => reagent.itemId > 0 && reagent.quantity > 0)
      .map((reagent) => ({
        itemId: reagent.itemId,
        quantity: reagent.quantity,
        sortOrder: reagent.sortOrder,
        ranks: reagent.ranks,
      })),
  };
}

function toNullableInt(value: number | null | undefined): number | null {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return null;
  return Number(value);
}
