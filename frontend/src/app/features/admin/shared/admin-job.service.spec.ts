import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { AdminApiService, AdminJob } from '@api/generated';
import { Subject, of, throwError } from 'rxjs';
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

  it('continues polling after a transient status request failure', () => {
    vi.useFakeTimers();
    api.getAdminJob
      .mockReturnValueOnce(throwError(() => new Error('temporary network error')))
      .mockReturnValueOnce(of(completedJob));

    service.trackJob(runningJob);
    vi.advanceTimersByTime(4000);

    expect(api.getAdminJob).toHaveBeenCalledTimes(2);
    expect(service.activeJob()).toEqual(completedJob);
  });

  it.each([403, 404])(
    'stops polling and unblocks the job after a terminal %i response',
    (status) => {
      vi.useFakeTimers();
      api.getAdminJob.mockReturnValue(throwError(() => new HttpErrorResponse({ status })));

      service.trackJob(runningJob);
      vi.advanceTimersByTime(4000);

      expect(api.getAdminJob).toHaveBeenCalledOnce();
      expect(service.activeJob()).toBeNull();
      expect(service.pollingError()).toBeTruthy();
    },
  );

  it('stops polling and unblocks the job after retry attempts are exhausted', () => {
    vi.useFakeTimers();
    api.getAdminJob.mockReturnValue(throwError(() => new Error('temporary network error')));

    service.trackJob(runningJob);
    vi.advanceTimersByTime(8000);

    expect(api.getAdminJob).toHaveBeenCalledTimes(3);
    expect(service.activeJob()).toBeNull();
    expect(service.pollingError()).toBeTruthy();
  });
});
