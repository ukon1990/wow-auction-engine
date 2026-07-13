import { ColumnDef, createColumnHelper } from '@tanstack/angular-table';

import type { ProfessionRecipeOverview } from './profession-talent-trees.page';

export type ProfessionRecipeRow = ProfessionRecipeOverview['recipes'][number];

type RecipeColumnMeta = {
  readonly align: 'left' | 'right';
  readonly gridTrack: string;
  readonly cardRole?: 'primary' | 'metric' | 'detail';
  readonly cardLabel?: string;
  readonly cardPriority?: number;
};

export function createProfessionRecipeColumns(): ColumnDef<ProfessionRecipeRow, unknown>[] {
  const helper = createColumnHelper<ProfessionRecipeRow>();
  return [
    helper.accessor('name', {
      header: $localize`:@@professionTalentTrees.recipeTable.recipe:Recipe`,
      enableSorting: false,
      meta: {
        align: 'left',
        gridTrack: 'minmax(14rem, 2fr)',
        cardRole: 'primary',
        cardPriority: 0,
      } satisfies RecipeColumnMeta,
    }),
    helper.accessor('recipeId', {
      header: $localize`:@@professionTalentTrees.recipeTable.recipeId:Recipe ID`,
      enableSorting: false,
      meta: {
        align: 'right',
        gridTrack: 'minmax(7rem, 0.7fr)',
        cardRole: 'metric',
        cardLabel: $localize`:@@professionTalentTrees.recipeTable.recipeId:Recipe ID`,
      } satisfies RecipeColumnMeta,
    }),
    helper.accessor((recipe) => recipe.craftedItemId ?? '—', {
      id: 'craftedItemId',
      header: $localize`:@@professionTalentTrees.recipeTable.craftedItemId:Crafted item ID`,
      enableSorting: false,
      meta: {
        align: 'right',
        gridTrack: 'minmax(9rem, 0.8fr)',
        cardRole: 'metric',
        cardLabel: $localize`:@@professionTalentTrees.recipeTable.craftedItemId:Crafted item ID`,
      } satisfies RecipeColumnMeta,
    }),
    helper.accessor('reagentSlotCount', {
      header: $localize`:@@professionTalentTrees.recipeTable.reagentSlots:Reagent slots`,
      enableSorting: false,
      meta: {
        align: 'right',
        gridTrack: 'minmax(8rem, 0.7fr)',
        cardRole: 'metric',
        cardLabel: $localize`:@@professionTalentTrees.recipeTable.reagentSlots:Reagent slots`,
      } satisfies RecipeColumnMeta,
    }),
    helper.accessor((recipe) => recipe.baseDifficulty ?? '—', {
      id: 'baseDifficulty',
      header: $localize`:@@professionTalentTrees.recipeTable.baseDifficulty:Base difficulty`,
      enableSorting: false,
      meta: {
        align: 'right',
        gridTrack: 'minmax(9rem, 0.8fr)',
        cardRole: 'metric',
        cardLabel: $localize`:@@professionTalentTrees.recipeTable.baseDifficulty:Base difficulty`,
      } satisfies RecipeColumnMeta,
    }),
  ] as ColumnDef<ProfessionRecipeRow, unknown>[];
}
