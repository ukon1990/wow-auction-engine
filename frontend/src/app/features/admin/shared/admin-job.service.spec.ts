import { TestBed } from '@angular/core/testing';
import { AdminApiService, AdminJob } from '@api/generated';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi, vitest } from 'vitest';
import { APPLY_EXPANSION_RANGES_OPERATION, AdminJobService } from './admin-job.service';

const runningJob: AdminJob = {
  id: 7,
  domain: AdminJob.DomainEnum.Item,
  operation: APPLY_EXPANSION_RANGES_OPERATION,
  status: AdminJob.StatusEnum.Running,
  startedAt: '2026-06-23T08:00:00Z',
};

const completedJob: AdminJob = {
  ...runningJob,
  status: AdminJob.StatusEnum.Completed,
  finishedAt: '2026-06-23T08:00:05Z',
  summary: {
    matchedItemCount: 10,
    updatedItemCount: 8,
    conflictItemCount: 1,
  },
};

describe('AdminJobService', () => {
  let service: AdminJobService;
  let api: { getAdminJob: ReturnType<typeof vitest.fn> };

  beforeEach(() => {
    api = {
      getAdminJob: vitest.fn().mockReturnValue(of(completedJob)),
    };

    TestBed.configureTestingModule({
      providers: [AdminJobService, { provide: AdminApiService, useValue: api }],
    });

    service = TestBed.inject(AdminJobService);
  });

  afterEach(() => {
    service.stopPolling();
    vi.useRealTimers();
  });

  it('tracks a running job until it completes', () => {
    vi.useFakeTimers();
    const response = new Subject<AdminJob>();
    api.getAdminJob.mockReturnValue(response);

    service.trackJob(runningJob);

    expect(service.activeJob()).toEqual(runningJob);

    vi.advanceTimersByTime(2000);
    response.next(completedJob);
    response.complete();

    expect(service.activeJob()).toEqual(completedJob);
    expect(api.getAdminJob).toHaveBeenCalledWith(7);
  });
});
