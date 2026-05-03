import { Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { FormField, email, form, required } from '@angular/forms/signals';

import { AdminEditableCellComponent } from '../market/admin-editable-cell.component';
import { CheckboxInputComponent } from './checkbox-input.component';
import { PillToggleComponent } from './pill-toggle.component';
import { SearchInputComponent } from './search-input.component';
import { SelectInputComponent } from './select-input.component';
import { TextInputComponent } from './text-input.component';

@Component({
  imports: [
    AdminEditableCellComponent,
    CheckboxInputComponent,
    PillToggleComponent,
    ReactiveFormsModule,
    SearchInputComponent,
    SelectInputComponent,
    TextInputComponent,
  ],
  template: `
    <ee-text-input label="Item" [formControl]="textControl" />
    <ee-search-input label="Search" [formControl]="searchControl" />
    <ee-select-input label="Profession" [options]="options" [formControl]="selectControl" />
    <ee-checkbox-input label="Profitable" [formControl]="checkboxControl" />
    <ee-pill-toggle
      label="Scope"
      [options]="scopeOptions"
      [value]="'realm'"
      [formControl]="scopeControl"
    />
    <ee-admin-editable-cell label="Yield" [formControl]="numberControl" />
  `,
})
class ReactiveFormControlHostComponent {
  readonly textControl = new FormControl('Dracothyst', { nonNullable: true });
  readonly searchControl = new FormControl('Awakened', { nonNullable: true });
  readonly selectControl = new FormControl('alchemy', { nonNullable: true });
  readonly checkboxControl = new FormControl(true, { nonNullable: true });
  readonly scopeControl = new FormControl('realm', { nonNullable: true });
  readonly numberControl = new FormControl('1.25', { nonNullable: true });

  readonly options = [
    { id: 'alchemy', label: 'Alchemy' },
    { id: 'enchanting', label: 'Enchanting' },
  ];

  readonly scopeOptions = [
    { id: 'realm', label: 'Realm' },
    { id: 'region', label: 'Region' },
  ];
}

@Component({
  imports: [TextInputComponent],
  template: `
    <ee-text-input label="Email" error="Email is required." [invalid]="true" [required]="true" />
  `,
})
class ErrorStateHostComponent {}

@Component({
  imports: [FormField, TextInputComponent],
  template: `<ee-text-input label="Email" [formField]="loginForm.email" />`,
})
class SignalFormTextHostComponent {
  readonly loginModel = signal({ email: '' });
  readonly loginForm = form(this.loginModel, (p) => {
    required(p.email, { message: 'Email is required' });
    email(p.email, { message: 'Enter a valid email' });
  });
}

describe('form controls', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ErrorStateHostComponent,
        ReactiveFormControlHostComponent,
        SignalFormTextHostComponent,
      ],
    }).compileComponents();
  });

  it('binds initial reactive form values into controls', () => {
    const fixture = TestBed.createComponent(ReactiveFormControlHostComponent);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const inputs = element.querySelectorAll('input');
    const select = element.querySelector('select');
    const checkbox = element.querySelector('ee-checkbox-input input');
    const activePill = element.querySelector('ee-pill-toggle button[aria-pressed="true"]');

    expect(inputs[0]?.value).toBe('Dracothyst');
    expect(inputs[1]?.value).toBe('Awakened');
    expect(select?.value).toBe('alchemy');
    expect((checkbox as HTMLInputElement | null)?.checked).toBe(true);
    expect(activePill?.textContent?.trim()).toBe('Realm');
  });

  it('writes user changes back to reactive form controls', () => {
    const fixture = TestBed.createComponent(ReactiveFormControlHostComponent);
    fixture.detectChanges();

    const host = fixture.componentInstance;
    const element = fixture.nativeElement as HTMLElement;
    const checkbox = element.querySelector('ee-checkbox-input input') as HTMLInputElement;
    const regionButton = element.querySelectorAll('ee-pill-toggle button')[1] as HTMLButtonElement;
    const select = element.querySelector('select') as HTMLSelectElement;

    checkbox.click();
    regionButton.click();
    select.value = 'enchanting';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(host.checkboxControl.value).toBe(false);
    expect(host.scopeControl.value).toBe('region');
    expect(host.selectControl.value).toBe('enchanting');
  });

  it('renders required and invalid state for form controls', () => {
    const fixture = TestBed.createComponent(ErrorStateHostComponent);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const input = element.querySelector('input');

    expect(input?.required).toBe(true);
    expect(input?.getAttribute('aria-invalid')).toBe('true');
    expect(element.textContent).toContain('Email is required.');
  });

  it('binds signal form field validation into text input', () => {
    const fixture = TestBed.createComponent(SignalFormTextHostComponent);
    fixture.detectChanges();

    const host = fixture.componentInstance;
    const input = (fixture.nativeElement as HTMLElement).querySelector('input') as HTMLInputElement;

    expect(host.loginForm.email().invalid()).toBe(true);
    expect(input.getAttribute('aria-invalid')).toBe('true');

    input.value = 'not-an-email';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(host.loginModel().email).toBe('not-an-email');
    expect(host.loginForm.email().invalid()).toBe(true);

    input.value = 'a@b.co';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(host.loginForm.email().invalid()).toBe(false);
    expect(input.getAttribute('aria-invalid')).toBe('false');
  });
});
