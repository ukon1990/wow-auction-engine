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

  it('renders the dialog when open', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Panel title');
  });

  it('emits closed when the backdrop is clicked', () => {
    const closed = vitest.fn();
    fixture.componentInstance.closed.subscribe(closed);
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const backdrop = fixture.nativeElement.querySelector('.fixed.inset-0') as HTMLElement;
    backdrop.click();

    expect(closed).toHaveBeenCalledOnce();
  });

  it('emits closed when Escape is pressed', () => {
    const closed = vitest.fn();
    fixture.componentInstance.closed.subscribe(closed);
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(closed).toHaveBeenCalledOnce();
  });
});
