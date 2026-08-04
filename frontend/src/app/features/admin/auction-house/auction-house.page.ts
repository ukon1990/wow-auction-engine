import { Component, effect, inject, signal } from '@angular/core';
import { AuctionHouseService } from '@features/admin/auction-house/auction-house.service';
import { AuctionHouse } from '@api/generated';
import { firstValueFrom } from 'rxjs';
import { PageFrameComponent, TableComponent } from '@ui';
import { createAuctionHouseColumns } from '@features/admin/auction-house/auction-house-table.columns';

@Component({
  selector: 'app-auction-house',
  imports: [PageFrameComponent, TableComponent],
  templateUrl: './auction-house.page.html',
  styleUrl: './auction-house.page.css',
})
export class AuctionHousePage {
  private readonly service = inject(AuctionHouseService);
  private hasInitiallyLoaded = false;
  readonly isLoading = signal<boolean>(false);
  readonly columns = createAuctionHouseColumns();
  readonly data = signal<AuctionHouse[]>([]);

  private readonly _loadDataEffect = effect(() => {
    const isLoading = this.isLoading();
    const data = this.data();
    if (isLoading || data.length > 0) return;
    firstValueFrom(
      this.service.getPageByQuery({
        // TODO: Unsure if we actually need to paginate this as there are not that many
        page: 0,
        pageSize: 0,
        query: '',
        sortBy: '',
        sortDirection: 'asc',
      }),
    )
      .then((page) => this.data.set(page?.items ?? []))
      .catch(console.error);
  });
}
