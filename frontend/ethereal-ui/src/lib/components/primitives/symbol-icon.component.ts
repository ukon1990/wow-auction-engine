import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type SymbolIconName =
  | 'account_circle'
  | 'add'
  | 'admin_panel_settings'
  | 'auto_stories'
  | 'chevron_left'
  | 'chevron_right'
  | 'construction'
  | 'deployed_code'
  | 'diamond'
  | 'filter_alt'
  | 'help'
  | 'import_export'
  | 'inventory_2'
  | 'keyboard_arrow_down'
  | 'keyboard_arrow_up'
  | 'close'
  | 'menu'
  | 'magic_button'
  | 'manage_accounts'
  | 'person'
  | 'query_stats'
  | 'schema'
  | 'search'
  | 'settings'
  | 'show_chart'
  | 'swords'
  | 'travel_explore'
  | 'water_medium';

@Component({
  selector: 'ee-symbol-icon',
  template: `
    <svg
      class="inline-block h-[1em] w-[1em] shrink-0"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      @switch (name()) {
        @case ('account_circle') {
          <circle cx="12" cy="12" r="9" />
          <circle cx="12" cy="9.5" r="3" />
          <path d="M6.8 19c1.2-3 3-4.5 5.2-4.5s4 1.5 5.2 4.5" />
        }
        @case ('add') {
          <path d="M12 5v14M5 12h14" />
        }
        @case ('admin_panel_settings') {
          <path d="M12 3 5 6v5.5c0 4.2 2.8 7.9 7 9.5 4.2-1.6 7-5.3 7-9.5V6l-7-3Z" />
          <path d="M9 12.5 11.2 15 15.5 9" />
        }
        @case ('auto_stories') {
          <path d="M4 6.5c2.7-.8 5-.4 7 1.2v10.8c-2-1.6-4.3-2-7-1.2V6.5Z" />
          <path d="M20 6.5c-2.7-.8-5-.4-7 1.2v10.8c2-1.6 4.3-2 7-1.2V6.5Z" />
        }
        @case ('chevron_left') {
          <path d="m15 18-6-6 6-6" />
        }
        @case ('chevron_right') {
          <path d="m9 18 6-6-6-6" />
        }
        @case ('construction') {
          <path d="m14.7 6.3 3 3M3 21l6-6M8.6 16.4l8.8-8.8a2.1 2.1 0 0 0-3-3L5.6 13.4l3 3Z" />
          <path d="m14 14 7 7" />
        }
        @case ('deployed_code') {
          <path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z" />
          <path d="M12 12 4.4 7.7M12 12l7.6-4.3M12 12v8.7" />
        }
        @case ('diamond') {
          <path d="M6.5 4h11L22 9l-10 11L2 9l4.5-5Z" />
          <path d="M2 9h20M8 4l4 16 4-16" />
        }
        @case ('filter_alt') {
          <path d="M4 5h16l-6 7v5l-4 2v-7L4 5Z" />
        }
        @case ('help') {
          <circle cx="12" cy="12" r="9" />
          <path d="M9.8 9a2.6 2.6 0 1 1 3.4 2.5c-.8.3-1.2.9-1.2 1.8v.4" />
          <path d="M12 17h.01" />
        }
        @case ('import_export') {
          <path d="M8 7h8M8 7l2.5-2.5M8 7l2.5 2.5M16 17H8M16 17l-2.5 2.5M16 17l-2.5-2.5" />
        }
        @case ('inventory_2') {
          <path d="M4 8h16v11H4V8Z" />
          <path d="M6 4h12l2 4H4l2-4ZM9 12h6" />
        }
        @case ('keyboard_arrow_down') {
          <path d="M8 10l4 4 4-4" />
        }
        @case ('keyboard_arrow_up') {
          <path d="M8 14l4-4 4 4" />
        }
        @case ('close') {
          <path d="M18 6 6 18M6 6l12 12" />
        }
        @case ('menu') {
          <path d="M4 7h16M4 12h16M4 17h16" />
        }
        @case ('magic_button') {
          <path d="m4 20 11-11" />
          <path
            d="m13 7 4 4M16 3l.6 2.1L19 6l-2.4.9L16 9l-.6-2.1L13 6l2.4-.9L16 3ZM6 5l.4 1.4L8 7l-1.6.6L6 9l-.4-1.4L4 7l1.6-.6L6 5Z"
          />
        }
        @case ('manage_accounts') {
          <circle cx="9" cy="8" r="3.5" />
          <path d="M3.5 20c1-3.8 2.8-5.7 5.5-5.7 1.7 0 3 .7 4.1 2" />
          <circle cx="17" cy="15" r="2" />
          <path
            d="M17 10.8v1.2M17 18v1.2M12.9 12.6l1 .6M20.1 16.8l1 .6M12.9 17.4l1-.6M20.1 13.2l1-.6"
          />
        }
        @case ('person') {
          <circle cx="12" cy="8" r="4" />
          <path d="M5 21c1.4-4 3.7-6 7-6s5.6 2 7 6" />
        }
        @case ('query_stats') {
          <path d="M4 19V5M4 19h16" />
          <path d="m7 15 3-4 3 2 5-7" />
          <circle cx="18" cy="6" r="2" />
        }
        @case ('schema') {
          <rect x="4" y="4" width="6" height="5" rx="1" />
          <rect x="14" y="4" width="6" height="5" rx="1" />
          <rect x="9" y="15" width="6" height="5" rx="1" />
          <path d="M7 9v2h5v4M17 9v2h-5" />
        }
        @case ('search') {
          <circle cx="11" cy="11" r="7" />
          <path d="m16.5 16.5 4 4" />
        }
        @case ('settings') {
          <circle cx="12" cy="12" r="3" />
          <path
            d="M19.4 15a1.8 1.8 0 0 0 .4 2l.1.1-2.1 2.1-.1-.1a1.8 1.8 0 0 0-2-.4 1.8 1.8 0 0 0-1.1 1.7V21h-3v-.6a1.8 1.8 0 0 0-1.1-1.7 1.8 1.8 0 0 0-2 .4l-.1.1-2.1-2.1.1-.1a1.8 1.8 0 0 0 .4-2 1.8 1.8 0 0 0-1.7-1.1H4v-3h.6a1.8 1.8 0 0 0 1.7-1.1 1.8 1.8 0 0 0-.4-2l-.1-.1 2.1-2.1.1.1a1.8 1.8 0 0 0 2 .4A1.8 1.8 0 0 0 11.1 4V3h3v1a1.8 1.8 0 0 0 1.1 1.7 1.8 1.8 0 0 0 2-.4l.1-.1 2.1 2.1-.1.1a1.8 1.8 0 0 0-.4 2 1.8 1.8 0 0 0 1.7 1.1h.4v3h-.4a1.8 1.8 0 0 0-1.7 1.1Z"
          />
        }
        @case ('show_chart') {
          <path d="M4 19V5M4 19h16" />
          <path d="m6 15 4-4 3 3 6-8" />
        }
        @case ('swords') {
          <path d="M14 4h6v6L8 22l-4-4L16 6" />
          <path d="m4 4 16 16M4 4h6M4 4v6" />
        }
        @case ('travel_explore') {
          <circle cx="11" cy="11" r="7" />
          <path
            d="M11 4c2 2.2 3 4.5 3 7s-1 4.8-3 7M11 4c-2 2.2-3 4.5-3 7s1 4.8 3 7M4 11h14M16.5 16.5 21 21"
          />
        }
        @case ('water_medium') {
          <path d="M12 3s6 6.2 6 11a6 6 0 0 1-12 0c0-4.8 6-11 6-11Z" />
          <path d="M9 14a3 3 0 0 0 3 3" />
        }
        @default {
          <circle cx="12" cy="12" r="9" />
        }
      }
    </svg>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SymbolIconComponent {
  readonly name = input.required<SymbolIconName | string>();
}
