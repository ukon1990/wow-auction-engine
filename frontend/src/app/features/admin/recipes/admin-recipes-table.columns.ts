import { ColumnDef, createColumnHelper, flexRenderComponent } from '@tanstack/angular-table';
import { AdminRecipe1 } from '@api/generated';
import { AdminRecipeActionsCellComponent } from './admin-recipe-actions-cell.component';

type AdminRecipeColumnMeta = {
  readonly align: 'left' | 'right';
  readonly gridTrack: string;
  readonly cardRole?: 'primary' | 'metric' | 'detail';
  readonly cardLabel?: string;
  readonly cardPriority?: number;
} & AdminRecipeTableActions;

export type AdminRecipeTableActions = {
  readonly onEdit: (recipe: AdminRecipe1) => void;
  readonly onCompare: (recipe: AdminRecipe1) => void;
  readonly onDeleteOverride: (recipe: AdminRecipe1) => void;
};

export const createAdminRecipeColumns = (actions: AdminRecipeTableActions) => {
  const stateOverrideLabel = $localize`:@@admin.recipes.table.stateOverride:Override`;
  const stateBaseLabel = $localize`:@@admin.recipes.table.stateBase:Base`;
  const helper = createColumnHelper<AdminRecipe1>();
  return [
    helper.accessor('id', {
      header: $localize`:@@admin.recipes.table.id:Recipe ID`,
      meta: {
        align: 'left',
        gridTrack: 'minmax(6rem, 0.6fr)',
        cardRole: 'metric',
        cardLabel: $localize`:@@admin.recipes.table.id:Recipe ID`,
        ...actions,
      } satisfies AdminRecipeColumnMeta,
    }),
    helper.accessor(
      (row) => row.effective.name ?? $localize`:@@admin.recipes.unnamed:Unnamed recipe`,
      {
        id: 'name',
        header: $localize`:@@admin.recipes.table.name:Name`,
        meta: {
          align: 'left',
          gridTrack: 'minmax(14rem, 1.8fr)',
          cardRole: 'primary',
          cardPriority: 0,
          ...actions,
        } satisfies AdminRecipeColumnMeta,
      },
    ),
    helper.accessor((row) => row.effective.professionName ?? '—', {
      id: 'profession',
      header: $localize`:@@admin.recipes.table.profession:Profession`,
      meta: { align: 'left', gridTrack: 'minmax(10rem, 1fr)', cardRole: 'detail', ...actions },
    }),
    helper.accessor((row) => outputSummary(row), {
      id: 'outputs',
      header: $localize`:@@admin.recipes.table.outputs:Outputs`,
      meta: { align: 'left', gridTrack: 'minmax(12rem, 1fr)', cardRole: 'detail', ...actions },
    }),
    helper.accessor((row) => String(row.effective.reagents?.length ?? 0), {
      id: 'reagents',
      header: $localize`:@@admin.recipes.table.reagents:Reagents`,
      meta: {
        align: 'right',
        gridTrack: 'minmax(6rem, 0.5fr)',
        cardRole: 'metric',
        cardLabel: $localize`:@@admin.recipes.table.reagents:Reagents`,
        ...actions,
      } satisfies AdminRecipeColumnMeta,
    }),
    helper.accessor((row) => (row.hasOverride ? stateOverrideLabel : stateBaseLabel), {
      id: 'state',
      header: $localize`:@@admin.recipes.table.state:State`,
      meta: { align: 'left', gridTrack: 'minmax(7rem, 0.6fr)', cardRole: 'detail', ...actions },
    }),
    helper.display({
      id: 'actions',
      header: $localize`:@@admin.recipes.table.actions:Actions`,
      meta: { align: 'right', gridTrack: 'minmax(9rem, 0.7fr)', ...actions },
      cell: () => flexRenderComponent(AdminRecipeActionsCellComponent),
    }),
  ] as ColumnDef<AdminRecipe1, unknown>[];
};

function outputSummary(recipe: AdminRecipe1): string {
  const outputs = recipe.effective.outputs ?? [];
  if (outputs.length === 0) return '—';
  return outputs
    .slice(0, 2)
    .map((output) => `${output.craftedItemName ?? output.craftedItemId} x${output.craftedQuantity}`)
    .join(', ');
}
