import { AdminItemJob } from '@api/generated';
import {
  APPLY_EXPANSION_RANGES_JOB,
  FETCH_EXPANSION_RANGE_ITEMS_JOB,
} from '@features/admin/expansions/admin-expansion-job.service';

export function expansionJobTitle(job: AdminItemJob): string {
  if (job.type === APPLY_EXPANSION_RANGES_JOB) {
    return job.status === AdminItemJob.StatusEnum.Running
      ? $localize`:@@admin.expansions.jobs.apply.running:Applying expansion ranges…`
      : $localize`:@@admin.expansions.jobs.apply.done:Apply expansion ranges finished`;
  }
  if (job.type === FETCH_EXPANSION_RANGE_ITEMS_JOB) {
    return job.status === AdminItemJob.StatusEnum.Running
      ? $localize`:@@admin.expansions.jobs.fetch.running:Fetching missing items…`
      : $localize`:@@admin.expansions.jobs.fetch.done:Fetch missing items finished`;
  }
  return $localize`:@@admin.expansions.jobs.generic:Admin item job`;
}

export function expansionJobSummary(job: AdminItemJob): string | null {
  if (job.status === AdminItemJob.StatusEnum.Running) {
    return null;
  }
  if (job.status === AdminItemJob.StatusEnum.Failed) {
    return job.errorMessage ?? $localize`:@@admin.expansions.jobs.failed:Job failed.`;
  }

  const summary = job.summary;
  if (!summary) {
    return null;
  }

  if (job.type === APPLY_EXPANSION_RANGES_JOB) {
    return [
      formatCount('matched', summary['matchedItemCount']),
      formatCount('updated', summary['updatedItemCount']),
      formatCount('conflicts', summary['conflictItemCount']),
    ]
      .filter((line): line is string => line !== null)
      .join(' · ');
  }

  if (job.type === FETCH_EXPANSION_RANGE_ITEMS_JOB) {
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
