import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AdminItemService } from '@features/admin/items/admin-item.service';
import { ItemsPage } from '@features/admin/items/items.page';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

describe('ItemsPage', () => {
  it('creates and triggers initial load', async () => {
    const load = vi.fn(() =>
      of({
        items: [],
        page: { page: 0, pageSize: 50, totalItems: 0, totalPages: 0 },
      }),
    );

    await TestBed.configureTestingModule({
      imports: [ItemsPage],
      providers: [
        {
          provide: AdminItemService,
          useValue: {
            loading: { asReadonly: () => false },
            mutationLoading: { asReadonly: () => false },
            error: { asReadonly: () => null },
            page: { asReadonly: () => null },
            load,
          },
        },
      ],
    }).compileComponents();

    const fixture: ComponentFixture<ItemsPage> = TestBed.createComponent(ItemsPage);
    fixture.detectChanges();

    expect(load).toHaveBeenCalled();
  });
});
