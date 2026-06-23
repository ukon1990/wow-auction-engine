import { routes, TitledRoutes } from './app.routes';

describe('routes', () => {
  it('redirects admin to status and exposes status as an admin child route', () => {
    const adminRoute = routes.find((route) => route.path === 'admin');
    const children = (adminRoute?.children ?? []) as TitledRoutes;
    const redirectRoute = children.find((route) => route.path === '');
    const statusRoute = children.find((route) => route.path === 'status');

    expect(redirectRoute?.redirectTo).toBe('status');
    expect(statusRoute?.title).toBe('Status');
    expect(statusRoute?.icon).toBe('query_stats');
    expect(statusRoute?.loadComponent).toBeTruthy();
  });
});
