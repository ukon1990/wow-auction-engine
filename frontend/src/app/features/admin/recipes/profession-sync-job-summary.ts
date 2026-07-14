import { AdminJob } from '@api/generated';

export interface ProfessionSyncProgressView {
  readonly percent: number | null;
  readonly primary: string;
  readonly secondary: string | null;
}

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
    return professionSyncJobProgress(job).secondary;
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

export function professionSyncJobProgress(job: AdminJob): ProfessionSyncProgressView {
  const summary = job.summary ?? {};
  const percent = readPercent(summary['progressPercent']);

  if (job.status !== AdminJob.StatusEnum.Running) {
    return {
      percent,
      primary: professionSyncJobTitle(job),
      secondary: professionSyncJobSummary(job),
    };
  }

  const phase = typeof summary['phase'] === 'string' ? summary['phase'] : null;
  const primary = runningProgressPrimary(summary, phase);
  const secondary = runningProgressSecondary(summary, phase);

  return { percent, primary, secondary };
}

function runningProgressPrimary(summary: Record<string, unknown>, phase: string | null): string {
  switch (phase) {
    case 'fetching_metadata':
      return $localize`:@@admin.recipes.professionSync.progressMetadata:Loading modified crafting metadata…`;
    case 'fetching_professions':
      return $localize`:@@admin.recipes.professionSync.progressProfessions:Fetching professions from Blizzard…`;
    case 'processing_skill_tier': {
      const professionIndex = readCount(summary['professionIndex']);
      const professionTotal = readCount(summary['professionTotal']);
      const professionName = readLabel(summary['professionName']);
      if (professionIndex != null && professionTotal != null && professionName) {
        return $localize`:@@admin.recipes.professionSync.progressProfession:Processing profession ${professionIndex}:INTERPOLATION:/${professionTotal}:INTERPOLATION_2: · ${professionName}:INTERPOLATION_3:`;
      }
      return $localize`:@@admin.recipes.professionSync.progressProcessing:Processing professions and skill tiers…`;
    }
    default:
      return $localize`:@@admin.recipes.professionSync.progressStarting:Starting profession sync…`;
  }
}

function runningProgressSecondary(summary: Record<string, unknown>, phase: string | null): string {
  const parts: string[] = [];

  if (phase === 'processing_skill_tier') {
    const skillTierIndex = readCount(summary['skillTierIndex']);
    const skillTierTotal = readCount(summary['skillTierTotal']);
    const skillTierName = readLabel(summary['skillTierName']);
    if (skillTierIndex != null && skillTierTotal != null && skillTierName) {
      parts.push(
        $localize`:@@admin.recipes.professionSync.progressSkillTier:Skill tier ${skillTierIndex}:INTERPOLATION:/${skillTierTotal}:INTERPOLATION_2: · ${skillTierName}:INTERPOLATION_3:`,
      );
    }

    const completed = readCount(summary['skillTiersCompleted']);
    const total = readCount(summary['skillTiersTotal']);
    if (completed != null && total != null) {
      parts.push(
        $localize`:@@admin.recipes.professionSync.progressTiersOverall:Tiers completed ${completed}:INTERPOLATION:/${total}:INTERPOLATION_2:`,
      );
    }

    const recipesInTier = readCount(summary['recipesInTier']);
    if (recipesInTier != null) {
      parts.push(
        $localize`:@@admin.recipes.professionSync.progressRecipesInTier:Recipes in tier ${recipesInTier}:INTERPOLATION:`,
      );
    }
  }

  const recipesFetched = readCount(summary['recipesFetched']);
  const recipeFailures = readCount(summary['recipeFailures']);
  if (recipesFetched != null) {
    parts.push(
      $localize`:@@admin.recipes.professionSync.progressRecipesFetched:Recipes fetched ${recipesFetched}:INTERPOLATION:`,
    );
  }
  if (recipeFailures != null && recipeFailures > 0) {
    parts.push(
      $localize`:@@admin.recipes.professionSync.progressRecipeFailures:Recipe failures ${recipeFailures}:INTERPOLATION:`,
    );
  }

  if (parts.length === 0) {
    return $localize`:@@admin.recipes.professionSync.runningDescription:You can continue browsing recipes while it finishes.`;
  }

  return parts.join(' · ');
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

function readCount(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function readPercent(value: unknown): number | null {
  const count = readCount(value);
  if (count == null) return null;
  return Math.max(0, Math.min(100, Math.round(count)));
}

function readLabel(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value : null;
}
