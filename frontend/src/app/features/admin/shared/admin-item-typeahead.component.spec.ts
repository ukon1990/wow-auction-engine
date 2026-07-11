import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AdminApiService } from '@api/generated';
import { LocaleService } from '@core/services/locale.service';
import { of } from 'rxjs';
import { AdminItemSelection, AdminItemTypeaheadComponent } from './admin-item-typeahead.component';

const item = {
  id: 224025,
  hasBase: true,
  hasOverride: false,
  effective: {
    name: 'Core Alloy',
    rank: 2,
    mediaUrl: 'https://render.worldofwarcraft.com/item/224025.jpg',
  },
};

describe('AdminItemTypeaheadComponent', () => {
  let fixture: ComponentFixture<AdminItemTypeaheadComponent>;
  const api = {
    searchAdminItems: vitest.fn(() =>
      of({
        items: [item],
        page: { page: 1, pageSize: 20, totalItems: 1, totalPages: 1 },
      }),
    ),
    getAdminItem: vitest.fn(() => of(item)),
  };

  beforeEach(async () => {
    vitest.useFakeTimers();
    api.searchAdminItems.mockClear();
    api.getAdminItem.mockClear();
    await TestBed.configureTestingModule({
      imports: [AdminItemTypeaheadComponent],
      providers: [
        { provide: AdminApiService, useValue: api },
        { provide: LocaleService, useValue: { apiLocaleOverride: () => 'en_US' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminItemTypeaheadComponent);
    fixture.componentRef.setInput('label', 'Item');
    fixture.detectChanges();
  });

  afterEach(() => vitest.useRealTimers());

  it('keeps typed text and emits the selected item name and id', async () => {
    const selections: Array<AdminItemSelection | null> = [];
    fixture.componentInstance.itemChange.subscribe((selection) => selections.push(selection));
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');

    input.value = 'Core';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(input.value).toBe('Core');
    expect(selections).toEqual([]);

    await vitest.advanceTimersByTimeAsync(200);
    fixture.detectChanges();
    const option: HTMLButtonElement = fixture.nativeElement.querySelector('[role="option"]');
    const icon: HTMLImageElement = option.querySelector('img')!;

    expect(icon.src).toBe('https://render.worldofwarcraft.com/item/224025.jpg');
    expect(icon.alt).toBe('');

    option.click();
    fixture.detectChanges();

    expect(input.value).toBe('Core Alloy');
    expect(selections).toEqual([{ id: 224025, name: 'Core Alloy' }]);
  });

  it('searches by item id after debounceWait without requesting on every keystroke', async () => {
    fixture.componentRef.setInput('debounceWait', 300);
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');

    for (const value of ['2', '22', '224025']) {
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    fixture.detectChanges();

    await vitest.advanceTimersByTimeAsync(299);
    expect(api.searchAdminItems).not.toHaveBeenCalled();

    await vitest.advanceTimersByTimeAsync(1);
    fixture.detectChanges();
    expect(api.searchAdminItems).toHaveBeenCalledTimes(1);
    expect(api.searchAdminItems).toHaveBeenCalledWith(
      '224025',
      'en_US',
      undefined,
      undefined,
      undefined,
      undefined,
      undefined,
      undefined,
      1,
      20,
    );
  });

  it('resolves an existing id to its localized item name', async () => {
    fixture.componentRef.setInput('itemId', 224025);
    fixture.detectChanges();
    await vitest.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    expect(api.getAdminItem).toHaveBeenCalledWith(224025, 'en_US', false, false);
    expect((fixture.nativeElement.querySelector('input') as HTMLInputElement).value).toBe(
      'Core Alloy',
    );
  });
});
