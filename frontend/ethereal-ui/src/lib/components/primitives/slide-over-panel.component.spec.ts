import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SlideOverPanelComponent } from './slide-over-panel.component';

describe('SlideOverPanelComponent', () => {
  let fixture: ComponentFixture<SlideOverPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SlideOverPanelComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SlideOverPanelComponent);
    fixture.componentRef.setInput('title', 'Panel title');
    await fixture.whenStable();
  });

  afterEach(() => {
    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();
    document.body.classList.remove('overflow-hidden');
  });

  it('renders the dialog when open', async () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(document.body.querySelector('[role="dialog"]')).toBeTruthy();
    expect(document.body.textContent).toContain('Panel title');
  });

  it('portals the panel to document body when open', async () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(document.body.querySelector('[role="dialog"]')).toBeTruthy();
    expect(document.body.querySelector('.z-\\[60\\]')).toBeTruthy();
  });

  it('emits closed when the backdrop is clicked', async () => {
    const closed = vitest.fn();
    fixture.componentInstance.closed.subscribe(closed);
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    await fixture.whenStable();

    const backdrop = document.body.querySelector('.fixed.inset-0') as HTMLElement;
    backdrop.click();

    expect(closed).toHaveBeenCalledOnce();
  });

  it('emits closed when Escape is pressed', async () => {
    const closed = vitest.fn();
    fixture.componentInstance.closed.subscribe(closed);
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    await fixture.whenStable();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(closed).toHaveBeenCalledOnce();
  });
});
