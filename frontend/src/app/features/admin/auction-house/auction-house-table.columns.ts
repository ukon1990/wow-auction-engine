import { AuctionHouse } from '@api/generated';
import { ColumnDef, flexRenderComponent } from '@tanstack/angular-table';
import { DateTimeColumnComponent } from '@ui';
import { AuctionHouseActionsCellComponent } from './auction-house-actions-cell.component';

export interface AuctionHouseActions {
  onDetails: (house: AuctionHouse) => void;
  onUpdateNow: (house: AuctionHouse) => void;
  onToggleAutoUpdate: (house: AuctionHouse) => void;
  isSaving: (house: AuctionHouse) => boolean;
}

export function createAuctionHouseColumns(
  actions: AuctionHouseActions,
): ColumnDef<AuctionHouse, unknown>[] {
  return [
    {
      id: 'connectedRealmId',
      accessorKey: 'connectedRealmId',
      header: $localize`:@@admin.auction-house.column.id:ID`,
      enableSorting: false,
      meta: {
        gridTrack: '3.5rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.id:ID`,
      },
    },
    {
      id: 'region',
      accessorKey: 'region',
      header: $localize`:@@admin.auction-house.column.region:Region`,
      meta: {
        gridTrack: '5rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.region:Region`,
      },
    },
    {
      id: 'realms',
      header: $localize`:@@admin.auction-house.column.realms:Realms`,
      accessorFn: (house) => house.realms,
      enableSorting: false,
      meta: { gridTrack: 'minmax(9rem, 2fr)', cardRole: 'primary' },
      cell: (info) => {
        const names = (info.row.original.realms ?? []).map((realm) => realm.name);
        return names.length > 2
          ? `${names.slice(0, 2).join(', ')} +${names.length - 2}`
          : names.join(', ') || '—';
      },
    },
    {
      id: 'lastModified',
      accessorKey: 'lastModified',
      header: $localize`:@@admin.auction-house.column.lastModified:Updated at`,
      meta: {
        gridTrack: '8rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.lastModified:Updated at`,
      },
      cell: () => flexRenderComponent(DateTimeColumnComponent),
    },
    {
      id: 'nextUpdate',
      accessorKey: 'nextUpdate',
      header: $localize`:@@admin.auction-house.column.nextUpdate:Next update`,
      meta: {
        gridTrack: '8rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.nextUpdate:Next update`,
      },
      cell: () => flexRenderComponent(DateTimeColumnComponent),
    },
    {
      id: 'avgDelay',
      accessorKey: 'avgDelay',
      header: $localize`:@@admin.auction-house.column.delays:Delay (min / avg / max)`,
      meta: {
        gridTrack: '9rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.delays:Delay (min / avg / max)`,
      },
      cell: (info) => {
        const house = info.row.original;
        return `${house.lowestDelay ?? '—'} / ${house.avgDelay ?? '—'} / ${house.highestDelay ?? '—'}`;
      },
    },
    {
      id: 'autoUpdate',
      accessorKey: 'autoUpdate',
      header: $localize`:@@admin.auction-house.column.autoUpdate:Auto update`,
      enableSorting: false,
      meta: {
        gridTrack: '5rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.autoUpdate:Auto update`,
      },
      cell: (info) =>
        info.row.original.autoUpdate
          ? $localize`:@@admin.auction-house.enabled:Enabled`
          : $localize`:@@admin.auction-house.disabled:Disabled`,
    },
    {
      id: 'actions',
      header: $localize`:@@admin.auction-house.actions:Actions`,
      enableSorting: false,
      meta: { gridTrack: '3.5rem', cardRole: 'detail', ...actions },
      cell: () => flexRenderComponent(AuctionHouseActionsCellComponent),
    },
  ];
}
