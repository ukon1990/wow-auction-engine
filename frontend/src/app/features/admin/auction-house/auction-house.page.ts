import { Component, effect, inject, signal } from '@angular/core';
import { AuctionHouseService } from '@features/admin/auction-house/auction-house.service';
import { AuctionHouse } from '@api/generated';

@Component({
  selector: 'app-auction-house',
  imports: [],
  templateUrl: './auction-house.page.html',
  styleUrl: './auction-house.page.css',
})
export class AuctionHousePage {
  private readonly service = inject(AuctionHouseService);
  private hasInitiallyLoaded = false;
  readonly isLoading = signal<boolean>(false);
  readonly data = signal<AuctionHouse[]>([]);

  private readonly _loadDataEffect = effect(() => {
    const isLoading = this.isLoading();
    const data = this.data();
    if (isLoading || data.length > 0) return;
  });
}
