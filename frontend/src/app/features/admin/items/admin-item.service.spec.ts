import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import {
  AdminApiService,
  AdminItem1,
  AdminItemOverrideRequest,
  AdminItemPage,
} from '@api/generated';
import { LocaleService } from '@core/services/locale.service';
import { ToastService } from '@core/services/toast.service';
import { defaultAdminItemFilters } from './item-filters';
import { AdminItemService } from './admin-item.service';

const itemFixture: AdminItem1 = {
  id: 19019,
  hasBase: true,
  hasOverride: true,
  effective: {
    name: 'Thunderfury',
    quality: { id: 5, type: 'LEGENDARY', name: 'Legendary' },
  },
  base: {
    name: 'Thunderfury',
  },
  override: {
    level: 80,
    itemClass: { id: 2, name: 'Weapon' },
    itemSubclass: { id: 7, name: 'Sword' },
    overrideNote: 'test',
  },
};

const pageFixture: AdminItemPage = {
  items: [itemFixture],
  page: {
    page: 1,
    pageSize: 25,
    totalItems: 1,
    totalPages: 1,
  },
};

describe('AdminItemService', () => {
  let service: AdminItemService;
  let api: {
    searchAdminItems: ReturnType<typeof vitest.fn>;
    getAdminItem: ReturnType<typeof vitest.fn>;
    upsertAdminItemOverride: ReturnType<typeof vitest.fn>;
    deleteAdminItemOverride: ReturnType<typeof vitest.fn>;
    searchAdminRecipes: ReturnType<typeof vitest.fn>;
    upsertAdminItemRecipeAssociation: ReturnType<typeof vitest.fn>;
    createAdminItem: ReturnType<typeof vitest.fn>;
    compareAdminItemWithApi: ReturnType<typeof vitest.fn>;
  };
  let toast: { error: ReturnType<typeof vitest.fn> };

  beforeEach(() => {
    api = {
      searchAdminItems: vitest.fn().mockReturnValue(of(pageFixture)),
      getAdminItem: vitest.fn().mockReturnValue(of(itemFixture)),
      upsertAdminItemOverride: vitest.fn().mockReturnValue(of(itemFixture)),
      deleteAdminItemOverride: vitest.fn().mockReturnValue(of(undefined)),
      searchAdminRecipes: vitest.fn().mockReturnValue(
        of([
          {
            recipeId: 338995,
            name: 'Craft Thunderfury',
            professionName: 'Blacksmithing',
            skillTierName: 'Classic',
            professionCategoryName: 'Weapons',
            craftedItemId: null,
            craftedItemName: null,
            craftedQuantity: null,
          },
        ]),
      ),
      upsertAdminItemRecipeAssociation: vitest.fn().mockReturnValue(of(itemFixture)),
      createAdminItem: vitest.fn().mockReturnValue(of(itemFixture)),
      compareAdminItemWithApi: vitest.fn().mockReturnValue(of({ itemId: 19019, fields: {} })),
    };
    toast = { error: vitest.fn() };

    TestBed.configureTestingModule({
      providers: [
        AdminItemService,
        { provide: AdminApiService, useValue: api },
        { provide: ToastService, useValue: toast },
        { provide: LocaleService, useValue: { apiLocaleOverride: () => 'en_US' } },
      ],
    });
    service = TestBed.inject(AdminItemService);
  });

  it('searches items with generated API parameters', () => {
    service
      .search({
        ...defaultAdminItemFilters(),
        name: 'thunder',
        hasOverride: 'true',
      })
      .subscribe();

    expect(api.searchAdminItems).toHaveBeenCalledWith(
      'thunder',
      'en_US',
      undefined,
      true,
      undefined,
      undefined,
      undefined,
      undefined,
      1,
      25,
    );
    expect(service.items()).toEqual([itemFixture]);
    expect(service.page()).toEqual(pageFixture.page);
  });

  it('loads item details with base and override rows', () => {
    service.loadItem(19019).subscribe();

    expect(api.getAdminItem).toHaveBeenCalledWith(19019, 'en_US', true, true);
    expect(service.selectedItem()).toEqual(itemFixture);
  });

  it('submits the provided full sparse override state on save', () => {
    const request: AdminItemOverrideRequest = {
      level: 80,
      requiredLevel: null,
      itemClassId: 2,
      itemSubclassId: 7,
      overrideNote: 'test',
    };

    service.upsertOverride(19019, request, defaultAdminItemFilters()).subscribe();

    expect(api.upsertAdminItemOverride).toHaveBeenCalledWith(19019, request);
    expect(api.searchAdminItems).toHaveBeenCalledOnce();
    expect(api.getAdminItem).toHaveBeenCalledWith(19019, 'en_US', true, true);
  });

  it('searches recipes with locale and limit', () => {
    service.searchRecipes('craft thunder', 10).subscribe();

    expect(api.searchAdminRecipes).toHaveBeenCalledWith('craft thunder', 'en_US', 10);
  });

  it('associates a recipe and reloads the item', () => {
    const request = { craftedItemId: 19019, craftedQuantity: 2 };

    service.upsertRecipeAssociation(19019, 338995, request, defaultAdminItemFilters()).subscribe();

    expect(api.upsertAdminItemRecipeAssociation).toHaveBeenCalledWith(19019, 338995, request);
    expect(api.searchAdminItems).toHaveBeenCalledOnce();
    expect(api.getAdminItem).toHaveBeenCalledWith(19019, 'en_US', true, true);
  });

  it('stores and reports search errors', () => {
    api.searchAdminItems.mockReturnValue(throwError(() => new Error('network')));

    service.search(defaultAdminItemFilters()).subscribe({ error: () => undefined });

    expect(service.error()).toBe('Unable to load items.');
    expect(toast.error).toHaveBeenCalledWith('Unable to load items.');
  });
});
