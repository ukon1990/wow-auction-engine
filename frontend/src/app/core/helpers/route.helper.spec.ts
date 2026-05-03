import { getActiveRouteLinks } from './route.helper';
import { TitledRoutes } from '../../app.routes';

describe('route helper', () => {
  const routes: TitledRoutes = [
    {
      path: 'auctions',
      title: 'Auctions',
      icon: 'travel_explore',
      canActivate: () => true,
    },
  ];
  it('should get active route links', () => {
    const links = getActiveRouteLinks(routes);
    expect(links).toBeDefined();
  });
});
