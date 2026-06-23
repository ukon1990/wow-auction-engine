import { AdminJob } from '@api/generated';
import {
  APPLY_EXPANSION_RANGES_OPERATION,
  FETCH_EXPANSION_RANGE_ITEMS_OPERATION,
} from '@features/admin/shared/admin-job.service';

export function expansionJobTitle(job: AdminJob): string {
  if (job.operation === APPLY_EXPANSION_RANGES_OPERATION) {
    return job.status === AdminJob.StatusEnum.Running
      ? $localize`:@@admin.expansions.jobs.apply.running:Applying expansion ranges…`
      : $localize`:@@admin.expansions.jobs.apply.done:Apply expansion ranges finished`;
  }
  if (job.operation === FETCH_EXPANSION_RANGE_ITEMS_OPERATION) {
    return job.status === AdminJob.StatusEnum.Running
      ? $localize`:@@admin.expansions.jobs.fetch.running:Fetching missing items…`
      : $localize`:@@admin.expansions.jobs.fetch.done:Fetch missing items finished`;
  }
  return $localize`:@@admin.expansions.jobs.generic:Admin job`;
}

export function expansionJobSummary(job: AdminJob): string | null {
  if (job.status === AdminJob.StatusEnum.Running) {
    return null;
  }
  if (job.status === AdminJob.StatusEnum.Failed) {
    return job.errorMessage ?? $localize`:@@admin.expansions.jobs.failed:Job failed.`;
  }

  const summary = job.summary;
  if (!summary) {
    return null;
  }

  if (job.operation === APPLY_EXPANSION_RANGES_OPERATION) {
    return [
      formatCount('matched', summary['matchedItemCount']),
      formatCount('updated', summary['updatedItemCount']),
      formatCount('conflicts', summary['conflictItemCount']),
    ]
      .filter((line): line is string => line !== null)
      .join(' · ');
  }

  if (job.operation === FETCH_EXPANSION_RANGE_ITEMS_OPERATION) {
    return [
      formatCount('missing', summary['missingItemCount']),
      formatCount('fetched', summary['fetchedItemCount']),
      formatCount('persisted', summary['persistedItemCount']),
      formatCount('failures', summary['itemFetchFailures']),
      formatDuration(summary['durationMs']),
    ]
      .filter((line): line is string => line !== null)
      .join(' · ');
  }

  return null;
}

function formatCount(label: string, value: unknown): string | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return null;
  }
  return `${label}: ${value}`;
}

function formatDuration(value: unknown): string | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return null;
  }
  return `durationMs: ${value}`;
}
