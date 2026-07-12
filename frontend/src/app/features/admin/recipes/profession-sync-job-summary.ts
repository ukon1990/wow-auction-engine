import { AdminJob } from '@api/generated';

export function professionSyncJobTitle(job: AdminJob): string {
  if (job.status === AdminJob.StatusEnum.Running) {
    return $localize`:@@admin.recipes.professionSync.running:Profession sync is running…`;
  }
  if (job.status === AdminJob.StatusEnum.Failed) {
    return $localize`:@@admin.recipes.professionSync.failed:Profession sync failed`;
  }
  return $localize`:@@admin.recipes.professionSync.completed:Profession sync completed`;
}

export function professionSyncJobSummary(job: AdminJob): string | null {
  if (job.status === AdminJob.StatusEnum.Running) {
    return $localize`:@@admin.recipes.professionSync.runningDescription:You can continue browsing recipes while it finishes.`;
  }
  if (job.status === AdminJob.StatusEnum.Failed) {
    return (
      job.errorMessage ??
      $localize`:@@admin.recipes.professionSync.failedDescription:The profession sync could not be completed.`
    );
  }

  const summary = job.summary;
  if (!summary) {
    return null;
  }

  return [
    formatCount(
      $localize`:@@admin.recipes.professionSync.professions:Professions`,
      summary['professionsFetched'],
    ),
    formatCount(
      $localize`:@@admin.recipes.professionSync.tiers:Skill tiers`,
      summary['skillTiersFetched'],
    ),
    formatCount(
      $localize`:@@admin.recipes.professionSync.recipes:Recipes`,
      summary['recipesFetched'],
    ),
    formatCount(
      $localize`:@@admin.recipes.professionSync.reagents:Reagents`,
      summary['reagentsReplaced'],
    ),
    formatCount(
      $localize`:@@admin.recipes.professionSync.failures:Recipe failures`,
      summary['recipeFailures'],
    ),
    formatDuration(summary['durationMs']),
  ]
    .filter((line): line is string => line !== null)
    .join(' · ');
}

function formatCount(label: string, value: unknown): string | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return null;
  }
  return `${label}: ${new Intl.NumberFormat().format(value)}`;
}

function formatDuration(value: unknown): string | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return null;
  }
  const seconds = value / 1000;
  return new Intl.NumberFormat(undefined, {
    style: 'unit',
    unit: 'second',
    maximumFractionDigits: seconds < 10 ? 1 : 0,
  }).format(seconds);
}
