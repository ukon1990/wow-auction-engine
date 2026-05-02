import { ChangeDetectionStrategy, Component } from '@angular/core';
import type { Routes } from '@angular/router';

/** Minimal route so `RouterLink` and `routerLinkActive` work in Storybook. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
})
export class StorybookRouterStubComponent {}

export const storybookRoutes: Routes = [{ path: '**', component: StorybookRouterStubComponent }];
