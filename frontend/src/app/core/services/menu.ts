import { Injectable, signal } from '@angular/core';
import { NavItem } from '@ui';

@Injectable({
  providedIn: 'root',
})
export class Menu {
  readonly links = signal<NavItem[]>([
    { id: 'dashboard', label: 'Dashboard', icon: 'dashboard' },
    { id: 'auctions', label: 'Auctions', icon: 'travel_explore' },
    { id: 'crafting', label: 'Crafting', icon: 'schema' },
    { id: 'archive', label: 'Archive', icon: 'inventory_2' },
  ]);
}
