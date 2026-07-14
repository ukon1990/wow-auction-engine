import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { AdminApiService, AdminJob } from '@api/generated';
import { LocaleService } from '@core/services/locale.service';
import { AdminExpansionService } from '@features/admin/expansions/admin-expansion.service';
import { AdminJobService } from '@features/admin/shared/admin-job.service';
import { of, Subject, throwError } from 'rxjs';
import { AdminRecipeService } from './admin-recipe.service';
import { RecipesPage } from './recipes.page';

const runningProfessionJob: AdminJob = {
  id: 59,
  domain: AdminJob.DomainEnum.Profession,
  operation: 'sync-professions',
  status: AdminJob.StatusEnum.Running,
  startedAt: '2026-07-12T09:00:00Z',
};

const completedProfessionJob: AdminJob = {
  ...runningProfessionJob,
  status: AdminJob.StatusEnum.Completed,
  finishedAt: '2026-07-12T09:00:05Z',
  summary: {
    professionsFetched: 11,
    skillTiersFetched: 24,
    recipesFetched: 137,
    reagentsReplaced: 420,
    recipeFailures: 2,
    durationMs: 5000,
  },
};

describe('RecipesPage', () => {
  let fixture: ComponentFixture<RecipesPage>;
  let recipeService: ReturnType<typeof createRecipeServiceStub>;
  let jobService: ReturnType<typeof createJobServiceStub>;

  beforeEach(async () => {
    recipeService = createRecipeServiceStub();
    jobService = createJobServiceStub();

    await TestBed.configureTestingModule({
      imports: [RecipesPage],
      providers: [
        { provide: AdminRecipeService, useValue: recipeService },
        {
          provide: AdminExpansionService,
          useValue: {
            expansions: signal([]),
            load: vitest.fn().mockReturnValue(of([])),
          },
        },
        { provide: AdminJobService, useValue: jobService },
        { provide: AdminApiService, useValue: { searchAdminItems: vitest.fn() } },
        {
          provide: LocaleService,
          useValue: { apiLocaleOverride: vitest.fn().mockReturnValue('en_US') },
        },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({})) } },
        { provide: Router, useValue: { navigate: vitest.fn().mockResolvedValue(true) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RecipesPage);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('starts the profession sync and disables the trigger while the job is running', async () => {
    recipeService.syncProfessionRecipes.mockReturnValue(of(runningProfessionJob));

    professionSyncButton().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(recipeService.syncProfessionRecipes).toHaveBeenCalledOnce();
    expect(jobService.trackJob).toHaveBeenCalledWith(runningProfessionJob);
    expect(professionSyncButton().disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Starting profession sync');
  });

  it('rehydrates a running profession sync and tracks it for polling', async () => {
    recipeService.activeProfessionJob.next(runningProfessionJob);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(recipeService.getActiveProfessionSyncJob).toHaveBeenCalledOnce();
    expect(jobService.trackJob).toHaveBeenCalledWith(runningProfessionJob);
    expect(professionSyncButton().disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Starting profession sync');
  });

  it('ignores a missing active profession sync', async () => {
    recipeService.activeProfessionJob.error(new HttpErrorResponse({ status: 404 }));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(jobService.trackJob).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).not.toContain('Profession sync is running');
  });

  it('does not resume polling when the page is destroyed before rehydration responds', () => {
    fixture.destroy();
    recipeService.activeProfessionJob.next(runningProfessionJob);

    expect(jobService.trackJob).not.toHaveBeenCalled();
  });

  it('renders a completed sync summary and lets the admin dismiss it', () => {
    jobService.activeJob.set(completedProfessionJob);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Profession sync completed');
    expect(fixture.nativeElement.textContent).toContain('Professions: 11');
    expect(fixture.nativeElement.textContent).toContain('Recipes: 137');

    const dismissButton = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.includes('Dismiss'));
    dismissButton?.click();
    expect(jobService.dismiss).toHaveBeenCalledOnce();
  });

  it('shows the duplicate-sync response inline', async () => {
    recipeService.syncProfessionRecipes.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );

    professionSyncButton().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('A profession sync is already running.');
  });

  it('stops job polling when the page is destroyed', () => {
    fixture.destroy();

    expect(jobService.stopPolling).toHaveBeenCalledOnce();
  });

  function professionSyncButton(): HTMLButtonElement {
    return Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.includes('Sync professions and recipes'))!;
  }
});

function createRecipeServiceStub() {
  const activeProfessionJob = new Subject<AdminJob | null>();
  return {
    activeProfessionJob,
    loading: signal(false),
    mutationLoading: signal(false),
    detailLoading: signal(false),
    compareLoading: signal(false),
    recipes: signal([]),
    page: signal({ page: 1, pageSize: 25, totalItems: 0, totalPages: 1 }),
    selectedRecipe: signal(null),
    compare: signal(null),
    error: signal<string | null>(null),
    detailError: signal<string | null>(null),
    compareError: signal<string | null>(null),
    search: vitest.fn().mockReturnValue(of([])),
    loadRecipe: vitest.fn(),
    upsertOverride: vitest.fn(),
    deleteOverride: vitest.fn(),
    compareWithApi: vitest.fn(),
    clearSelection: vitest.fn(),
    syncProfessionRecipes: vitest.fn(),
    getActiveProfessionSyncJob: vitest.fn().mockReturnValue(activeProfessionJob),
  };
}

function createJobServiceStub() {
  const activeJob = signal<AdminJob | null>(null);
  const dismissed = signal(false);
  return {
    activeJob,
    dismissed,
    pollingError: signal<string | null>(null),
    trackJob: vitest.fn((job: AdminJob) => activeJob.set(job)),
    dismiss: vitest.fn(() => dismissed.set(true)),
    stopPolling: vitest.fn(),
  };
}
