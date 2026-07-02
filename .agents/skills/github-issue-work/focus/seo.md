# SSR & SEO

Pass to **frontend workers**, **Bugbot**, and **maintainability reviewer** when public/indexable
routes change.

Reference: `docs/architecture/seo.md`, [touch-map.md](../../github-issue-planning/touch-map.md).

**Public/indexable pages: market browser, item detail, crafting browser.

## Required for indexable pages

- Data fetched in **Router Resolver** on server (SSR), not only in `ngOnInit` client-side
- **`TransferState`** passes resolver data to client — no duplicate fetch on hydration
- **Title + meta description** set per route/locale
- **Canonical URL** for duplicate/locale variants
- **Locale-prefixed routes** consistent with existing `app.routes.ts` patterns
- **JSON-LD** (Recipe, etc.) when page type matches existing structured-data patterns

## Reviewer checks

- [ ] New public route has resolver + TransferState (or documented exception)
- [ ] Metadata present in SSR HTML (not client-only `document.title`)
- [ ] Links use clean URLs (`/{locale}/r/{slug}`, etc.)
- [ ] `@defer` only where progressive enhancement is intentional — core content in SSR HTML
- [ ] Images use responsive/optimized patterns where applicable

## Bugbot trigger

When diff touches `app.routes.ts`, `*.resolver.ts`, or public `features/**` pages, apply this checklist.
