import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { AdminApiService, AdminItemJob } from '@api/generated';
import {
  AdminExpansionJobService,
  APPLY_EXPANSION_RANGES_JOB,
} from './admin-expansion-job.service';

const runningJob: AdminItemJob = {
  id: 7,
  type: APPLY_EXPANSION_RANGES_JOB,
  status: AdminItemJob.StatusEnum.Running,
  startedAt: '2026-06-23T10:00:00Z',
};

const completedJob: AdminItemJob = {
  ...runningJob,
  status: AdminItemJob.StatusEnum.Completed,
  finishedAt: '2026-06-23T10:00:05Z',
  summary: {
    matchedItemCount: 10,
    updatedItemCount: 8,
    conflictItemCount: 1,
  },
};

describe('AdminExpansionJobService', () => {
  let service: AdminExpansionJobService;
  let api: { getAdminItemJob: ReturnType<typeof vitest.fn> };

  beforeEach(() => {
    vitest.useFakeTimers();
    api = {
      getAdminItemJob: vitest.fn().mockReturnValue(of(completedJob)),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: AdminApiService, useValue: api }],
    });
    service = TestBed.inject(AdminExpansionJobService);
  });

  afterEach(() => {
    service.stopPolling();
    vitest.useRealTimers();
  });

  it('polls until the job reaches a terminal status', async () => {
    const response = new Subject<AdminItemJob>();
    api.getAdminItemJob.mockReturnValue(response);

    service.trackJob(runningJob);
    expect(service.activeJob()).toEqual(runningJob);

    await vitest.advanceTimersByTimeAsync(2000);
    response.next(runningJob);

    await vitest.advanceTimersByTimeAsync(2000);
    response.next(completedJob);
    response.complete();

    expect(api.getAdminItemJob).toHaveBeenCalledWith(7);
    expect(service.activeJob()).toEqual(completedJob);
  });

  it('dismisses the banner without clearing the active job', () => {
    service.trackJob(completedJob);
    service.dismiss();

    expect(service.dismissed()).toBe(true);
    expect(service.activeJob()).toEqual(completedJob);
  });
});
