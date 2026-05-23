import { Component, Type, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ColumnDef, createColumnHelper, flexRenderComponent } from '@tanstack/angular-table';

import { TableComponent } from '../../table.component';
import { DateColumnComponent, DateTimeColumnComponent } from './date-column.component';

type TestRow = {
  readonly id: string;
  readonly value: string;
};

const rows: TestRow[] = [{ id: 'row-1', value: '2026-05-23T09:15:00.000Z' }];
const helper = createColumnHelper<TestRow>();

@Component({
  imports: [TableComponent],
  template: ` <ee-table [columns]="columns()" [data]="rows" [getRowId]="getRowId" /> `,
})
class DateColumnHostComponent {
  readonly rows = rows;
  readonly columns = signal<ColumnDef<TestRow, unknown>[]>([]);
  readonly getRowId = (row: TestRow) => row.id;
}

describe('DateColumnComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DateColumnHostComponent],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = createHostWithCell(DateColumnComponent);

    expect(fixture.nativeElement.textContent).toContain('2026');
  });
});

describe('DateTimeColumnComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DateColumnHostComponent],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = createHostWithCell(DateTimeColumnComponent);

    expect((fixture.nativeElement.textContent as string).trim()).not.toBe('Value');
  });
});

function createHostWithCell(component: Type<unknown>): ComponentFixture<DateColumnHostComponent> {
  const fixture = TestBed.createComponent(DateColumnHostComponent);
  fixture.componentInstance.columns.set([
    helper.accessor('value', {
      header: 'Value',
      cell: () => flexRenderComponent(component),
    }),
  ] as ColumnDef<TestRow, unknown>[]);
  fixture.detectChanges();
  return fixture;
}
