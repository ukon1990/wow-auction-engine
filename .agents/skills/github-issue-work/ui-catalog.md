# UI component catalog (wow-auction-engine)

Reference for the **UI/UX expert** and **frontend workers**. Source of truth:
`frontend/ethereal-ui/src/lib/components/`.

**Rule:** Feature code uses existing ethereal-ui components. Do not duplicate form controls,
tables, charts, or layout shells. New primitives belong in `ethereal-ui/` with a Storybook story.

## Storybook

```bash
cd frontend && bun run storybook        # dev (ethereal-ui project)
cd frontend && bun run build-storybook
```

Stories live in `frontend/ethereal-ui/src/stories/ethereal-ui/`. New **shared** components get at least one
dedicated story export — add to the matching group file or a new `*.stories.ts` sibling.

Pattern: `@storybook/angular` `Meta` + `StoryObj`, `moduleMetadata({ imports: [...] })`.
Copy from existing stories under `frontend/ethereal-ui/src/stories/`.

## Shell & layout

| Component              | Path under ethereal-ui        | Use for                          |
| ---------------------- | ----------------------------- | -------------------------------- |
| `PageFrameComponent`   | `shell/page-frame.component`  | Page layout frame                |
| `TopNavComponent`      | `shell/top-nav.component`     | Top navigation                   |
| `SideNavComponent`     | `shell/side-nav.component`    | Side navigation                  |

## Forms

| Component              | Path under ethereal-ui        | Use for                          |
| ---------------------- | ----------------------------- | -------------------------------- |
| `TextInputComponent`   | `forms/text-input.component`  | Text fields                      |
| `SelectInputComponent` | `forms/select-input.component`| Select dropdowns                 |
| `CheckboxInputComponent`| `forms/checkbox-input.component` | Boolean toggles               |
| `SearchInputComponent` | `forms/search-input.component`| Search fields                    |
| `PillToggleComponent`  | `forms/pill-toggle.component` | Toggle pill groups               |

## Primitives

| Component              | Path under ethereal-ui        | Use for                          |
| ---------------------- | ----------------------------- | -------------------------------- |
| `IconButtonComponent`  | `primitives/icon-button`      | Icon-only actions                |
| `BadgeComponent`       | `primitives/badge`            | Status pills                     |
| `QualityBadgeComponent`| `primitives/quality-badge`    | WoW item quality colors          |
| `CurrencyAmountComponent`| `primitives/currency-amount`| Gold/silver/copper display       |
| `DropdownComponent`    | `primitives/dropdown`         | Menu actions                     |
| `SlideOverPanelComponent`| `primitives/slide-over-panel`| Side panels                     |
| `GlassPanelComponent`  | `primitives/glass-panel`      | Glass-style containers           |
| `CopyButtonComponent`  | `primitives/copy-button`      | Copy-to-clipboard                |
| `TooltipCardComponent` | `primitives/tooltip-card`     | Tooltip overlays                 |
| `SymbolIconComponent`  | `primitives/symbol-icon`      | Material symbol icons            |

## Market & tables

| Component              | Path under ethereal-ui        | Use for                          |
| ---------------------- | ----------------------------- | -------------------------------- |
| `TableComponent`       | `table/table.component`       | Data tables (TanStack)           |
| `PaginationComponent`  | `table/pagination.component`  | Table pagination                 |
| `FilterPanelComponent` | `market/filter-panel`         | Market filter sidebar            |
| `ItemTooltipCardComponent`| `market/item-tooltip-card` | Item tooltip cards               |
| `ItemStatCardComponent`| `market/item-stat-card`       | Item stat summaries              |
| `HeatmapGridComponent` | `market/heatmap-grid`         | Crafting heatmaps                |
| `AdminEditableCellComponent`| `market/admin-editable-cell`| Inline admin editing          |
| `ChartComponent`       | `charts/chart.component`      | Highcharts wrapper               |
| `ChartPanelComponent`  | `charts/chart-panel`          | Chart with panel chrome          |

## Feature-local components

Keep **feature-local** components in `features/<name>/` when:

- Layout is unique to one route (e.g. `market-quality-cell.component.ts`)
- Composition of existing ethereal-ui pieces is enough

Examples: `features/market-browser/market-item-cell.component.ts`,
`features/crafting/crafting-percent-cell.component.ts`.

## When to propose a new shared component

Propose **new ethereal-ui** (not feature-local) when:

- The same UI pattern appears in 2+ features or is listed in issue acceptance criteria for multiple screens
- It wraps design-system tokens the way existing ethereal-ui components do
- It is a primitive (button-like, card-like, form control), not page-specific layout

New shared components **must** include a Storybook story before the frontend slice is done.

## UX patterns to copy

| Pattern                        | Reference                                         |
| ------------------------------ | ------------------------------------------------- |
| Market browser table + filters | `features/market-browser/market-browser.page.ts`  |
| Item detail + charts           | `features/market-browser/market-item-detail.page.ts` |
| Crafting browser               | `features/crafting/crafting-browser.page.ts`      |
| Admin SQL editor               | `features/admin/status/admin-sql-editor.component.ts` |
| Admin data tables              | `features/admin/expansions/expansions.page.ts`    |
