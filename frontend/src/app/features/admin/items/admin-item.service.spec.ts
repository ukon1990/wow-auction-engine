import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { AdminApiService, AdminItemPage } from '@api/generated';
import { LocaleService } from '@core/services/locale.service';
import { ToastService } from '@core/services/toast.service';
import { AdminItemService } from '@features/admin/items/admin-item.service';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

describe('AdminItemService', () => {
  const page: AdminItemPage = {
    items: [],
    page: { page: 0, pageSize: 50, totalItems: 0, totalPages: 0 },
  };

  it('loads items and stores the page', async () => {
    const api = {
      listAdminItems: vi.fn(() => of(page)),
    };
    const toast = { error: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        AdminItemService,
        { provide: AdminApiService, useValue: api },
        { provide: ToastService, useValue: toast },
        { provide: LocaleService, useValue: { apiLocaleOverride: () => 'en_GB' } },
      ],
    });

    const service = TestBed.inject(AdminItemService);
    await new Promise<void>((resolve) => {
      service.load({ page: 0, pageSize: 50, sort: 'id' }).subscribe({
        next: () => resolve(),
      });
    });

    expect(api.listAdminItems).toHaveBeenCalled();
    expect(service.page()).toEqual(page);
    expect(service.loading()).toBe(false);
  });

  it('surfaces API errors', async () => {
    const api = {
      listAdminItems: vi.fn(() =>
        throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'boom' } })),
      ),
    };
    const toast = { error: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        AdminItemService,
        { provide: AdminApiService, useValue: api },
        { provide: ToastService, useValue: toast },
        { provide: LocaleService, useValue: { apiLocaleOverride: () => undefined } },
      ],
    });

    const service = TestBed.inject(AdminItemService);
    await new Promise<void>((resolve) => {
      service.load({ page: 0, pageSize: 50, sort: 'id' }).subscribe({
        error: () => resolve(),
      });
    });

    expect(service.error()).toBe('boom');
    expect(toast.error).toHaveBeenCalledWith('boom');
  });
});
