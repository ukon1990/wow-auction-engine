import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { AdminExpansion, AdminExpansionItemRange } from '@api/generated';
import { AdminExpansionJobService } from './admin-expansion-job.service';
import { AdminExpansionService } from './admin-expansion.service';
import { ExpansionsPage } from './expansions.page';

const expansionFixture: AdminExpansion = {
  id: 1,
  slug: 'vanilla',
  name: 'Vanilla',
  majorVersion: 1,
  displayOrder: 1,
};

const rangeFixture: AdminExpansionItemRange = {
  id: 10,
  expansion: expansionFixture,
  startItemId: 1,
  endItemId: 100,
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
      expansions: signal([expansionFixture]),
      ranges: signal([rangeFixture]),
      error: signal<string | null>(null),
      load: vitest.fn().mockReturnValue(of([[expansionFixture], [rangeFixture]])),
      createRange: vitest.fn(),
      updateRange: vitest.fn(),
      deleteRange: vitest.fn(),
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
        { provide: AdminExpansionJobService, useValue: jobStub },
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
  });

  it('opens the slide-over when Add range is clicked', () => {
    fixture.detectChanges();

    const addButton = Array.from(fixture.nativeElement.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Add range'),
    ) as HTMLButtonElement | undefined;
    expect(addButton).toBeTruthy();
    addButton?.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Add expansion range');
    expect(fixture.nativeElement.textContent).toContain('Create range');
  });
});
