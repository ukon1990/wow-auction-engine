import { ComponentFixture, TestBed } from '@angular/core/testing';
import type { CellContext } from '@tanstack/table-core';

import { DateColumnComponent, DateTimeColumnComponent } from './date-column.component';

const { flexRenderContext } = vi.hoisted(() => ({
  flexRenderContext: {
    getValue: () => '2026-05-23T09:15:00.000Z',
  } as CellContext<unknown, unknown>,
}));

vi.mock('@tanstack/angular-table', () => {
  return {
    injectFlexRenderContext: () => flexRenderContext,
  };
});

describe('DateColumnComponent', () => {
  let component: DateColumnComponent;
  let fixture: ComponentFixture<DateColumnComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DateColumnComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DateColumnComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

describe('DateTimeColumnComponent', () => {
  let component: DateTimeColumnComponent;
  let fixture: ComponentFixture<DateTimeColumnComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DateTimeColumnComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DateTimeColumnComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
