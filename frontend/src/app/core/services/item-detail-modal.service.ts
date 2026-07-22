import { Injectable, signal } from '@angular/core';

import type { ItemDetailOpenParams } from '@core/services/item-detail-url.helpers';
import type { ItemDetailScope } from '@core/services/market-item-detail.service';

export type ItemDetailModalState = ItemDetailOpenParams & {
  readonly scope: ItemDetailScope;
};

@Injectable({
  providedIn: 'root',
})
export class ItemDetailModalService {
  private readonly stateSignal = signal<ItemDetailModalState | null>(null);

  readonly state = this.stateSignal.asReadonly();

  open(params: ItemDetailOpenParams): void {
    this.stateSignal.set({
      itemId: params.itemId,
      bonusKey: params.bonusKey ?? '',
      modifierKey: params.modifierKey ?? '',
      petSpeciesId: params.petSpeciesId ?? 0,
      scope: params.scope ?? 'realm',
      recipeId: params.recipeId ?? null,
    });
  }

  updateScope(scope: ItemDetailScope): void {
    const current = this.stateSignal();
    if (!current) return;
    this.stateSignal.set({ ...current, scope });
  }

  close(): void {
    this.stateSignal.set(null);
  }
}
