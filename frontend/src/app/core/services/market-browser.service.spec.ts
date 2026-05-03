import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { MarketBrowserService } from './market-browser.service';

describe('MarketBrowserService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideRouter([])],
    });
  });

  it('starts with an empty API-backed Market Browser view model', () => {
    const service = TestBed.inject(MarketBrowserService);
    const viewModel = service.viewModel();

    expect(viewModel.activePrimaryNavId).toBe('market-browser');
    expect(viewModel.rows.length).toBe(0);
    expect(viewModel.paginationSummary).toBe('Loading market items...');
  });
});
