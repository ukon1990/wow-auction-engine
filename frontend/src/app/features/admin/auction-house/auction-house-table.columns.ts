import { ColumnDef, flexRenderComponent } from '@tanstack/angular-table';
import { AuctionHouse } from '@api/generated';
import { DateTimeColumnComponent } from '@ui';

export function createAuctionHouseColumns(): ColumnDef<AuctionHouse, unknown>[] {
  return [
    {
      id: 'connectedRealmId',
      accessorKey: 'connectedRealmId',
      header: $localize`:@@admin.auction-house.column.id:Id`,
      meta: {
        align: 'left',
        gridTrack: '4rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.id:Id`,
        cardPriority: 40,
      },
    },
    {
      id: 'region',
      accessorKey: 'region',
      header: 'Region',
      meta: {
        align: 'left',
        gridTrack: '5rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.region:Region`,
        cardPriority: 10,
      },
    },
    {
      id: 'realms',
      accessorKey: 'realms',
      header: 'Realms',
      meta: {
        align: 'left',
        gridTrack: 'minmax(5rem, 10rem)',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.region:Region`,
        cardPriority: 10,
      },
      cell: (info) => info.getValue(),
    },
    {
      id: 'lastModified',
      accessorKey: 'lastModified',
      header: $localize`:@@admin.auction-house.column.lastModified:Updated at`,
      meta: {
        align: 'left',
        gridTrack: '10rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.lastModified:Updated at`,
        cardPriority: 10,
      },
      cell: () => flexRenderComponent(DateTimeColumnComponent),
    },
    {
      id: 'nextUpdate',
      accessorKey: 'nextUpdate',
      header: $localize`:@@admin.auction-house.column.nextUpdate:Next update`,
      meta: {
        align: 'left',
        gridTrack: '10rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.nextUpdate:Next update`,
        cardPriority: 10,
      },
      cell: () => flexRenderComponent(DateTimeColumnComponent),
    },
    {
      id: 'lowestDelay',
      accessorKey: 'lowestDelay',
      header: $localize`:@@admin.auction-house.column.lowestDelay:Lowest delay`,
      meta: {
        align: 'left',
        gridTrack: '5rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.lowestDelay:Lowest delay`,
        cardPriority: 40,
      },
    },
    {
      id: 'avgDelay',
      accessorKey: 'avgDelay',
      header: $localize`:@@admin.auction-house.column.avgDelay:Avg delay`,
      meta: {
        align: 'left',
        gridTrack: '5rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.avgDelay:Avg delay`,
        cardPriority: 40,
      },
    },
    {
      id: 'highestDelay',
      accessorKey: 'highestDelay',
      header: $localize`:@@admin.auction-house.column.highestDelay:Highest delay`,
      meta: {
        align: 'left',
        gridTrack: '5rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.highestDelay:Highest delay`,
        cardPriority: 40,
      },
    },
    {
      id: 'lastDailyPriceUpdate',
      accessorKey: 'lastDailyPriceUpdate',
      header: $localize`:@@admin.auction-house.column.lastDailyPriceUpdate:Daily updated`,
      meta: {
        align: 'left',
        gridTrack: '10rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.lastDailyPriceUpdate:Daily updated`,
        cardPriority: 10,
      },
      cell: () => flexRenderComponent(DateTimeColumnComponent),
    },
    {
      id: 'lastHistoryDeleteEvent',
      accessorKey: 'lastHistoryDeleteEvent',
      header: $localize`:@@admin.auction-house.column.lastHistoryDeleteEvent:Deleted hourly`,
      meta: {
        align: 'left',
        gridTrack: '10rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.lastHistoryDeleteEvent:Deleted hourly`,
        cardPriority: 10,
      },
      cell: () => flexRenderComponent(DateTimeColumnComponent),
    },
    {
      id: 'lastHistoryDeleteEventDaily',
      accessorKey: 'lastHistoryDeleteEventDaily',
      header: $localize`:@@admin.auction-house.column.lastHistoryDeleteEventDaily:Deleted daily`,
      meta: {
        align: 'left',
        gridTrack: '10rem',
        cardRole: 'detail',
        cardLabel: $localize`:@@admin.auction-house.column.lastHistoryDeleteEventDaily:Deleted daily`,
        cardPriority: 10,
      },
      cell: () => flexRenderComponent(DateTimeColumnComponent),
    },
  ];
}
