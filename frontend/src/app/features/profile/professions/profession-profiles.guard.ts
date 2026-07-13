import { CanDeactivateFn } from '@angular/router';

import { ProfessionProfilesPage } from './profession-profiles.page';

export const pendingProfessionProfileChangesGuard: CanDeactivateFn<ProfessionProfilesPage> = (
  component,
) => component.confirmDiscardChanges();
