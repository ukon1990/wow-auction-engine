import { AdminJob } from '@api/generated';
import {
  professionSyncJobProgress,
  professionSyncJobSummary,
  professionSyncJobTitle,
} from './profession-sync-job-summary';

describe('professionSyncJobSummary', () => {
  it('formats running progress from job summary', () => {
    const job: AdminJob = {
      id: 1,
      domain: AdminJob.DomainEnum.Profession,
      operation: 'sync-professions',
      status: AdminJob.StatusEnum.Running,
      startedAt: '2026-07-14T13:00:00Z',
      summary: {
        phase: 'processing_skill_tier',
        professionIndex: 3,
        professionTotal: 12,
        professionName: 'Alchemy',
        skillTierIndex: 2,
        skillTierTotal: 5,
        skillTierName: 'Kul Tiran Alchemy',
        skillTiersCompleted: 8,
        skillTiersTotal: 47,
        recipesInTier: 42,
        recipesFetched: 1200,
        recipeFailures: 1,
        progressPercent: 25,
      },
    };

    const progress = professionSyncJobProgress(job);

    expect(progress.percent).toBe(25);
    expect(progress.primary).toContain('3/12');
    expect(progress.primary).toContain('Alchemy');
    expect(progress.secondary).toContain('2/5');
    expect(progress.secondary).toContain('Kul Tiran Alchemy');
    expect(progress.secondary).toContain('8/47');
    expect(progress.secondary).toContain('1200');
  });

  it('keeps completed summary stats', () => {
    const job: AdminJob = {
      id: 2,
      domain: AdminJob.DomainEnum.Profession,
      operation: 'sync-professions',
      status: AdminJob.StatusEnum.Completed,
      startedAt: '2026-07-14T13:00:00Z',
      finishedAt: '2026-07-14T13:05:00Z',
      summary: {
        professionsFetched: 11,
        skillTiersFetched: 24,
        recipesFetched: 137,
        reagentsReplaced: 420,
        recipeFailures: 2,
        durationMs: 5000,
      },
    };

    expect(professionSyncJobTitle(job)).toContain('completed');
    expect(professionSyncJobSummary(job)).toContain('Professions: 11');
    expect(professionSyncJobSummary(job)).toContain('Recipes: 137');
  });
});
