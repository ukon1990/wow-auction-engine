import { TestBed } from '@angular/core/testing';

import { MarketBrowserService } from './market-browser.service';

describe('MarketBrowserService', () => {
  it('returns the dummy Market Browser view model', () => {
    const service = TestBed.inject(MarketBrowserService);
    const viewModel = service.viewModel();

    expect(viewModel.activePrimaryNavId).toBe('market-browser');
    expect(viewModel.rows.length).toBeGreaterThan(0);
    expect(viewModel.rows.some((row) => row.selected)).toBe(true);
  });
});
