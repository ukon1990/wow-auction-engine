import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CopyButtonComponent } from './copy-button.component';

describe('CopyButtonComponent', () => {
  let fixture: ComponentFixture<CopyButtonComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CopyButtonComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CopyButtonComponent);
    fixture.componentRef.setInput('text', 'SELECT 1');
    fixture.detectChanges();
  });

  it('copies text to the clipboard', async () => {
    const writeText = vitest.fn().mockResolvedValue(undefined);
    Object.assign(navigator, {
      clipboard: { writeText },
    });

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(writeText).toHaveBeenCalledWith('SELECT 1');
    expect(fixture.nativeElement.textContent).toContain('Copied');
  });

  it('disables the button when text is empty', () => {
    fixture.componentRef.setInput('text', '   ');
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });
});
