import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { AdminExpansion, AdminExpansionItemRange } from '@api/generated';
import { englishGameLocale } from '@features/admin/shared/game-locale-test-fixtures';
import { AdminJobService } from '@features/admin/shared/admin-job.service';
import { AdminExpansionService } from './admin-expansion.service';
import { ExpansionsPage } from './expansions.page';

const vanillaExpansion: AdminExpansion = {
  id: 1,
  slug: 'vanilla',
  name: 'Vanilla',
  nameLocales: englishGameLocale('Vanilla'),
  majorVersion: 1,
  displayOrder: 1,
};

const midnightExpansion: AdminExpansion = {
  id: 12,
  slug: 'midnight',
  name: 'Midnight',
  nameLocales: englishGameLocale('Midnight'),
  majorVersion: 12,
  displayOrder: 120,
};

const rangeFixture: AdminExpansionItemRange = {
  id: 10,
  expansion: midnightExpansion,
  startItemId: 260000,
  endItemId: 274578,
  source: 'manual',
  enabled: true,
  createdAt: '2026-06-23T10:00:00Z',
  updatedAt: '2026-06-23T10:00:00Z',
};

describe('ExpansionsPage', () => {
  let fixture: ComponentFixture<ExpansionsPage>;

  beforeEach(async () => {
    const serviceStub = {
      loading: signal(false),
      mutationLoading: signal(false),
      expansions: signal([vanillaExpansion, midnightExpansion]),
      ranges: signal([rangeFixture]),
      error: signal<string | null>(null),
      load: vitest
        .fn()
        .mockReturnValue(of([[vanillaExpansion, midnightExpansion], [rangeFixture]])),
      createRange: vitest.fn(),
      updateRange: vitest.fn(),
      deleteRange: vitest.fn(),
      createExpansion: vitest.fn(),
      updateExpansion: vitest.fn(),
      deleteExpansion: vitest.fn(),
      startApplyJob: vitest.fn(),
      startFetchMissingJob: vitest.fn(),
    };
    const jobStub = {
      activeJob: signal(null),
      dismissed: signal(false),
      stopPolling: vitest.fn(),
      dismiss: vitest.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ExpansionsPage],
      providers: [
        { provide: AdminExpansionService, useValue: serviceStub },
        { provide: AdminJobService, useValue: jobStub },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ExpansionsPage);
    await fixture.whenStable();
  });

  it('creates and loads expansion data on init', () => {
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Expansion catalog');
    expect(fixture.nativeElement.textContent).toContain('Vanilla');
    expect(fixture.nativeElement.textContent).toContain('Midnight');
  });

  it('opens the slide-over with create defaults when Add range is clicked', async () => {
    fixture.detectChanges();

    const addButton = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.includes('Add range'));
    expect(addButton).toBeTruthy();
    addButton?.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(document.body.querySelector('[role="dialog"]')).toBeTruthy();
    expect(document.body.textContent).toContain('Add expansion range');
    expect(document.body.textContent).toContain('Create range');

    const expansionSelect = document.body.querySelector(
      'app-expansion-range-form select',
    ) as HTMLSelectElement;
    const numberInputs = Array.from(
      document.body.querySelectorAll('app-expansion-range-form input[type="number"]'),
    ) as HTMLInputElement[];

    expect(expansionSelect.value).toBe('12');
    expect(numberInputs[0].value).toBe('274578');
    expect(numberInputs[1].value).toBe('');
  });
});
