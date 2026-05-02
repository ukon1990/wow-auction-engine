import { TestBed } from '@angular/core/testing';

import { MarketBrowserPage } from './market-browser.page';

describe('MarketBrowserPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarketBrowserPage],
    }).compileComponents();
  });

  it('renders the market browser shell with service data', () => {
    const fixture = TestBed.createComponent(MarketBrowserPage);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Market Browser');
    expect(compiled.textContent).toContain('Awakened Order');
    expect(compiled.textContent).toContain('Showing 1-4 of 1,248 items');
  });
});
