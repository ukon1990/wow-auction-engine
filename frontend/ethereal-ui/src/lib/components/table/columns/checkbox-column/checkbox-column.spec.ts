import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ColumnDef, flexRenderComponent } from '@tanstack/angular-table';

import { TableComponent } from '../../table.component';
import { CheckboxColumn } from './checkbox-column';

type TestRow = { id: string; value: string };

@Component({
  imports: [TableComponent],
  template: `<ee-table [columns]="columns" [data]="rows" [getRowId]="getRowId" />`,
})
class CheckboxColumnHostComponent {
  readonly rows: TestRow[] = [{ id: 'row-1', value: 'checked' }];
  readonly columns: ColumnDef<TestRow, unknown>[] = [
    {
      accessorKey: 'value',
      header: 'Value',
      cell: () => flexRenderComponent(CheckboxColumn),
    },
  ];
  readonly getRowId = (row: TestRow) => row.id;
}

describe('CheckboxColumn', () => {
  it('renders the cell value inside a table', async () => {
    await TestBed.configureTestingModule({
      imports: [CheckboxColumnHostComponent],
    }).compileComponents();

    const fixture = TestBed.createComponent(CheckboxColumnHostComponent);
    await fixture.whenStable();

    const checkbox = fixture.nativeElement.querySelector(
      'input[type="checkbox"]',
    ) as HTMLInputElement | null;
    expect(checkbox?.value).toBe('checked');
  });
});
