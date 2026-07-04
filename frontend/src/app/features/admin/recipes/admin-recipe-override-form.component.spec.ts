import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AdminRecipe1 } from '@api/generated';
import {
  AdminRecipeOverrideFormComponent,
  normalizeRequest,
} from './admin-recipe-override-form.component';

const sampleRecipe: AdminRecipe1 = {
  id: 42,
  hasBase: true,
  hasOverride: false,
  effective: {
    name: 'Test Recipe',
    professionName: 'Alchemy',
    skillTierName: 'Dragonflight',
    professionCategoryName: 'Potions',
    outputs: [],
    reagents: [],
  },
};

describe('AdminRecipeOverrideFormComponent', () => {
  let fixture: ComponentFixture<AdminRecipeOverrideFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminRecipeOverrideFormComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminRecipeOverrideFormComponent);
    fixture.componentRef.setInput('recipe', sampleRecipe);
    await fixture.whenStable();
  });

  it('renders recipe header', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('#42');
    expect(fixture.nativeElement.textContent).toContain('Test Recipe');
    expect(fixture.nativeElement.textContent).toContain('Alchemy');
  });

  it('adds output row', () => {
    fixture.detectChanges();

    const initialRows = fixture.nativeElement.querySelectorAll('.admin-row-grid').length;
    const addButton = (
      Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]
    ).find((button) => button.textContent?.includes('Add output'));

    addButton?.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.admin-row-grid').length).toBe(initialRows + 1);
  });

  it('validates quantity filtering on submit output', () => {
    const submitted = vitest.fn();
    fixture.componentInstance.submitted.subscribe(submitted);
    fixture.detectChanges();

    const addButton = (
      Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]
    ).find((button) => button.textContent?.includes('Add output'));
    addButton?.click();
    fixture.detectChanges();

    const rows = Array.from(
      fixture.nativeElement.querySelectorAll('.admin-row-grid'),
    ) as HTMLElement[];
    const outputRow = rows.at(-1);
    expect(outputRow).toBeTruthy();

    const inputs = outputRow!.querySelectorAll(
      'input[type="number"]',
    ) as NodeListOf<HTMLInputElement>;
    inputs[0].value = '123';
    inputs[0].dispatchEvent(new Event('input'));
    inputs[1].value = '0';
    inputs[1].dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form: HTMLFormElement = fixture.nativeElement.querySelector('form');
    form.requestSubmit();

    expect(submitted).toHaveBeenCalledTimes(1);
    expect(submitted.mock.calls[0][0].outputs).toEqual([]);
  });
});

describe('normalizeRequest', () => {
  it('filters invalid outputs and reagents', () => {
    expect(
      normalizeRequest({
        outputs: [
          { craftedItemId: 0, craftedQuantity: 1, sortOrder: 0 },
          { craftedItemId: 123, craftedQuantity: 0, sortOrder: 1 },
          { craftedItemId: 456, craftedQuantity: 2, sortOrder: 2 },
        ],
        reagents: [
          { itemId: 0, quantity: 2, sortOrder: 0, ranks: [] },
          { itemId: 789, quantity: 0, sortOrder: 1, ranks: [] },
          { itemId: 321, quantity: 3, sortOrder: 2, ranks: [] },
        ],
      }),
    ).toEqual({
      craftedItemId: null,
      craftedQuantity: null,
      rank: null,
      requiredSkillLevel: null,
      overrideNote: null,
      outputs: [{ craftedItemId: 456, craftedQuantity: 2, sortOrder: 2 }],
      reagents: [{ itemId: 321, quantity: 3, sortOrder: 2, ranks: [] }],
    });
  });
});
