import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CheckboxColumn } from './checkbox-column';

describe('CheckboxColumn', () => {
  let component: CheckboxColumn;
  let fixture: ComponentFixture<CheckboxColumn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CheckboxColumn],
    }).compileComponents();

    fixture = TestBed.createComponent(CheckboxColumn);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
