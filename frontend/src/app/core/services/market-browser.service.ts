import { Injectable, signal } from '@angular/core';

import { MarketBrowserViewModel } from '../models/market-browser.models';

@Injectable({
  providedIn: 'root',
})
export class MarketBrowserService {
  private readonly marketBrowser = signal<MarketBrowserViewModel>({
    primaryNavItems: [
      { id: 'scrying-pool', label: 'Scrying Pool', icon: 'query_stats' },
      { id: 'market-browser', label: 'Market Browser', icon: 'travel_explore' },
      { id: 'crafting-matrix', label: 'Crafting Matrix', icon: 'schema' },
      { id: 'archive', label: 'Archive', icon: 'inventory_2' },
    ],
    activePrimaryNavId: 'market-browser',
    professionNavItems: [
      { id: 'alchemy', label: 'Alchemy', icon: 'water_medium' },
      { id: 'blacksmithing', label: 'Blacksmithing', icon: 'swords' },
      { id: 'enchanting', label: 'Enchanting', icon: 'magic_button' },
      { id: 'jewelcrafting', label: 'Jewelcrafting', icon: 'diamond' },
      { id: 'inscription', label: 'Inscription', icon: 'auto_stories' },
    ],
    activeProfessionId: 'blacksmithing',
    character: {
      name: 'GoblinKing99',
      realm: 'Illidan-US',
      level: 70,
      profession: 'Blacksmithing',
      skill: 'Skill Level 300/300',
    },
    filterSections: [
      {
        id: 'expansion',
        label: 'Expansion',
        options: [
          { id: 'dragonflight', label: 'Dragonflight', selected: false },
          { id: 'shadowlands', label: 'Shadowlands', selected: false },
          { id: 'battle-for-azeroth', label: 'Battle for Azeroth', selected: false },
        ],
      },
      {
        id: 'quality',
        label: 'Quality',
        options: [
          { id: 'common', label: 'Common', selected: false, quality: 'common' },
          { id: 'uncommon', label: 'Uncommon', selected: true, quality: 'uncommon' },
          { id: 'rare', label: 'Rare', selected: true, quality: 'rare' },
          { id: 'epic', label: 'Epic', selected: true, quality: 'epic' },
          { id: 'legendary', label: 'Legendary', selected: false, quality: 'legendary' },
        ],
      },
    ],
    tableColumns: [
      { id: 'item', label: 'Item' },
      { id: 'quality', label: 'Quality' },
      { id: 'min-buyout', label: 'Min Buyout', align: 'right' },
      { id: 'market-value', label: 'Market Value', align: 'right' },
      { id: 'regional-average', label: 'Regional Avg', align: 'right' },
      { id: 'sale-rate', label: 'Sale Rate', align: 'right' },
    ],
    rows: [
      {
        id: 'dracothyst',
        name: 'Dracothyst',
        quality: 'epic',
        minBuyout: { gold: 3450 },
        marketValue: { gold: 3510 },
        regionalAverage: { gold: 3480 },
        saleRate: 0.85,
      },
      {
        id: 'awakened-order',
        name: 'Awakened Order',
        quality: 'rare',
        minBuyout: { gold: 420 },
        marketValue: { gold: 415 },
        regionalAverage: { gold: 418 },
        saleRate: 0.92,
        selected: true,
      },
      {
        id: 'silken-gemdust',
        name: 'Silken Gemdust',
        quality: 'uncommon',
        minBuyout: { gold: 12 },
        marketValue: { gold: 15 },
        regionalAverage: { gold: 14 },
        saleRate: 0.45,
      },
      {
        id: 'hochenblume',
        name: 'Hochenblume',
        quality: 'common',
        minBuyout: { gold: 2 },
        marketValue: { gold: 2 },
        regionalAverage: { gold: 2 },
        saleRate: 0.99,
      },
    ],
    paginationSummary: 'Showing 1-4 of 1,248 items',
  });

  readonly viewModel = this.marketBrowser.asReadonly();

  setActivePrimaryNavId(id: string): void {
    this.marketBrowser.update((vm) => ({ ...vm, activePrimaryNavId: id }));
  }

  setActiveProfessionId(id: string): void {
    this.marketBrowser.update((vm) => ({ ...vm, activeProfessionId: id }));
  }
}
