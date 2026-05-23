import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { Meta, StoryObj } from '@storybook/angular';

import { DropdownComponent, SymbolIconComponent } from '../../../public-api';

@Component({
  imports: [DropdownComponent, SymbolIconComponent],
  template: `
    <div class="flex min-h-48 items-start bg-background p-8 text-on-surface">
      <ee-dropdown
        [open]="open()"
        [buttonClass]="buttonClass"
        [panelClass]="panelClass"
        ariaLabel="Open actions"
        (toggle)="open.update((value) => !value)"
        (close)="open.set(false)"
      >
        <ng-container eeDropdownTrigger>
          <ee-symbol-icon class="text-[18px]" name="settings" />
          <span>Actions</span>
        </ng-container>
        <ng-container eeDropdownPanel>
          <button type="button" class="dropdown-row">Refresh Data</button>
          <button type="button" class="dropdown-row">Export Snapshot</button>
          <button type="button" class="dropdown-row">Archive View</button>
        </ng-container>
      </ee-dropdown>
    </div>
  `,
  styles: [
    `
      .dropdown-row {
        display: flex;
        width: 100%;
        padding: 0.75rem 1rem;
        text-align: left;
        font-family: var(--font-cinzel);
        font-size: 0.875rem;
        font-weight: 700;
        letter-spacing: 0;
        text-transform: uppercase;
        color: rgb(203 213 225);
        transition:
          color 150ms ease,
          background-color 150ms ease;
      }

      .dropdown-row:hover {
        background: rgb(255 255 255 / 0.05);
        color: var(--color-on-surface);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class DropdownStoryHostComponent {
  protected readonly open = signal(true);
  protected readonly buttonClass =
    'inline-flex items-center gap-2 rounded border border-white/10 bg-surface-container px-4 py-2 ee-label text-on-surface transition hover:bg-white/5';
  protected readonly panelClass =
    'absolute left-0 top-full z-[70] mt-2 min-w-56 overflow-hidden rounded border border-white/10 bg-slate-950/95 py-1 shadow-[0_12px_32px_rgba(0,0,0,0.55)] backdrop-blur-xl';
}

const meta: Meta<DropdownStoryHostComponent> = {
  title: 'Ethereal UI/Primitives/Dropdown',
  component: DropdownStoryHostComponent,
};

export default meta;

export const Dropdown: StoryObj<DropdownStoryHostComponent> = {};
