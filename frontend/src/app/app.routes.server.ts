import { RenderMode, ServerRoute } from '@angular/ssr';

/**
 * Hybrid rendering: only the realm picker (`/`) is prerendered at build time.
 * `/:region/:realm/...` is fully dynamic — prerendering would require `getPrerenderParams`
 * for every realm slug; we render those on the server per request instead.
 */
export const serverRoutes: ServerRoute[] = [
  {
    path: '',
    renderMode: RenderMode.Prerender,
  },
  {
    path: '**',
    renderMode: RenderMode.Server,
  },
];
