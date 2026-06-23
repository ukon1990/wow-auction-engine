import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AdminExpansion } from '@api/generated';
import { ExpansionRangeFormComponent } from './expansion-range-form.component';

const expansionFixture: AdminExpansion = {
  id: 1,
  slug: 'vanilla',
  name: 'Vanilla',
  majorVersion: 1,
  displayOrder: 1,
};

describe('ExpansionRangeFormComponent', () => {
  let fixture: ComponentFixture<ExpansionRangeFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExpansionRangeFormComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ExpansionRangeFormComponent);
    fixture.componentRef.setInput('expansions', [expansionFixture]);
    fixture.componentRef.setInput('mode', 'create');
    await fixture.whenStable();
  });

  it('renders without ngModel form registration errors', () => {
    const errorSpy = vitest.spyOn(console, 'error').mockImplementation(() => undefined);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Create range');
    expect(errorSpy).not.toHaveBeenCalled();
    errorSpy.mockRestore();
  });

  it('emits submitted with a valid payload', () => {
    const submitted = vitest.fn();
    fixture.componentInstance.submitted.subscribe(submitted);
    fixture.detectChanges();

    const expansionSelect = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    expansionSelect.value = '1';
    expansionSelect.dispatchEvent(new Event('change'));

    const numberInputs = Array.from(
      fixture.nativeElement.querySelectorAll('input[type="number"]'),
    ) as HTMLInputElement[];
    numberInputs[0].value = '10';
    numberInputs[0].dispatchEvent(new Event('input'));
    numberInputs[1].value = '20';
    numberInputs[1].dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form: HTMLFormElement = fixture.nativeElement.querySelector('form');
    form.requestSubmit();

    expect(submitted).toHaveBeenCalledWith({
      expansionId: 1,
      startItemId: 10,
      endItemId: 20,
      source: 'manual',
      enabled: true,
      note: null,
    });
  });
});
