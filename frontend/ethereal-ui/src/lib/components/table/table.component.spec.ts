import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ColumnDef, createColumnHelper } from '@tanstack/angular-table';
import type { SortingState } from '@tanstack/table-core';

import { TableComponent } from './table.component';
import { PaginationState } from './pagination.component';

type TestRow = {
  readonly id: string;
  readonly name: string;
  readonly quantity: number;
};

const helper = createColumnHelper<TestRow>();

const columns = [
  helper.accessor('name', {
    header: 'Name',
    meta: {
      align: 'left',
      gridTrack: 'minmax(10rem, 2fr)',
      cardRole: 'primary',
      cardPriority: 0,
    },
  }),
  helper.accessor('quantity', {
    header: 'Quantity',
    meta: {
      align: 'right',
      cardRole: 'metric',
      cardLabel: 'Qty',
      cardPriority: 10,
    },
  }),
] as ColumnDef<TestRow, unknown>[];

const unsortedRows: TestRow[] = [
  { id: 'charlie', name: 'Charlie', quantity: 3 },
  { id: 'alice', name: 'Alice', quantity: 1 },
];

@Component({
  imports: [TableComponent],
  template: `
    <ee-table
      [columns]="columns"
      [data]="rows()"
      [getRowId]="getRowId"
      (sortingChange)="sortingEvents.push($event)"
    />
  `,
})
class MinimalTableHostComponent {
  readonly columns = columns;
  readonly rows = signal(unsortedRows);
  readonly sortingEvents: SortingState[] = [];
  readonly getRowId = (row: TestRow) => row.id;
}

@Component({
  imports: [TableComponent],
  template: `
    <ee-table
      [columns]="columns"
      [data]="rows()"
      [getRowId]="getRowId"
      [manualSorting]="true"
      [sorting]="sorting()"
      (sortingChange)="sortingEvents.push($event)"
    />
  `,
})
class ManualSortingTableHostComponent {
  readonly columns = columns;
  readonly rows = signal(unsortedRows);
  readonly sorting = signal<SortingState>([]);
  readonly sortingEvents: SortingState[] = [];
  readonly getRowId = (row: TestRow) => row.id;
}

@Component({
  imports: [TableComponent],
  template: `
    <ee-table
      [columns]="columns"
      [data]="rows()"
      [getRowId]="getRowId"
      [paginationState]="paginationState()"
      [showFooter]="true"
      [showPagination]="true"
      (pageChange)="pageChanges.push($event)"
    />
  `,
})
class PaginatedTableHostComponent {
  readonly columns = columns;
  readonly rows = signal(unsortedRows);
  readonly paginationState = signal<PaginationState>({
    page: 0,
    pageSize: 10,
    totalItems: 42,
    totalPages: 5,
  });
  readonly pageChanges: number[] = [];
  readonly getRowId = (row: TestRow) => row.id;
}

@Component({
  imports: [TableComponent],
  template: `
    <ee-table
      [cardView]="true"
      [columns]="columns"
      [data]="rows()"
      [getRowId]="getRowId"
      [manualSorting]="true"
      [mobileSortOptions]="mobileSortOptions"
      [paginationState]="paginationState()"
      [showFooter]="true"
      [showPagination]="true"
      [sorting]="sorting()"
      (pageChange)="pageChanges.push($event)"
      (sortingChange)="onSortingChange($event)"
    />
  `,
})
class CardTableHostComponent {
  readonly columns = columns;
  readonly rows = signal(unsortedRows);
  readonly sorting = signal<SortingState>([{ id: 'name', desc: false }]);
  readonly sortingEvents: SortingState[] = [];
  readonly pageChanges: number[] = [];
  readonly mobileSortOptions = [
    { id: 'name', label: 'Name' },
    { id: 'quantity', label: 'Quantity' },
  ];
  readonly paginationState = signal<PaginationState>({
    page: 0,
    pageSize: 10,
    totalItems: 42,
    totalPages: 5,
  });
  readonly getRowId = (row: TestRow) => row.id;

  onSortingChange(next: SortingState): void {
    this.sorting.set(next);
    this.sortingEvents.push(next);
  }
}

describe('TableComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        MinimalTableHostComponent,
        ManualSortingTableHostComponent,
        PaginatedTableHostComponent,
        CardTableHostComponent,
      ],
    }).compileComponents();
  });

  it('renders with only columns and data using default non-clickable body rows', () => {
    const fixture = TestBed.createComponent(MinimalTableHostComponent);
    fixture.detectChanges();

    const bodyRows = getBodyRows(fixture);

    expect(bodyRows).toHaveLength(2);
    expect(bodyRows.every((row) => row.tagName === 'DIV')).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Charlie');
    expect(fixture.nativeElement.textContent).toContain('Alice');
  });

  it('infers grid tracks from column metadata and fallback tracks', () => {
    const fixture = TestBed.createComponent(MinimalTableHostComponent);
    fixture.detectChanges();

    const headerRow = getHeaderRow(fixture);

    expect(headerRow.style.gridTemplateColumns).toContain('minmax(10rem, 2fr)');
    expect(headerRow.style.gridTemplateColumns).toContain('minmax(12rem, 1fr)');
  });

  it('sorts rows locally by default and emits the next sorting state', () => {
    const fixture = TestBed.createComponent(MinimalTableHostComponent);
    fixture.detectChanges();

    clickHeader(fixture, 'Name');
    fixture.detectChanges();

    const bodyText = getBodyRows(fixture).map((row) => row.textContent ?? '');
    expect(bodyText[0]).toContain('Alice');
    expect(bodyText[1]).toContain('Charlie');
    expect(fixture.componentInstance.sortingEvents.at(-1)).toEqual([{ id: 'name', desc: false }]);
  });

  it('emits manual sorting changes without reordering rows locally', () => {
    const fixture = TestBed.createComponent(ManualSortingTableHostComponent);
    fixture.detectChanges();

    clickHeader(fixture, 'Name');
    fixture.detectChanges();

    const bodyText = getBodyRows(fixture).map((row) => row.textContent ?? '');
    expect(bodyText[0]).toContain('Charlie');
    expect(bodyText[1]).toContain('Alice');
    expect(fixture.componentInstance.sortingEvents.at(-1)).toEqual([{ id: 'name', desc: false }]);
  });

  it('renders shared pagination and forwards page changes', () => {
    const fixture = TestBed.createComponent(PaginatedTableHostComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Showing 1-10 of 42 rows');

    clickButton(fixture, 'Page 2');

    expect(fixture.componentInstance.pageChanges).toEqual([1]);
  });

  it('renders mobile card rows without table headers', () => {
    const fixture = TestBed.createComponent(CardTableHostComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(getHeaderRowOrNull(fixture)).toBeNull();
    expect(compiled.textContent).toContain('Charlie');
    expect(compiled.textContent).toContain('Qty');
    expect(compiled.textContent).toContain('3');
  });

  it('emits sorting changes from mobile sort controls', () => {
    const fixture = TestBed.createComponent(CardTableHostComponent);
    fixture.detectChanges();

    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    select.value = 'quantity';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    clickButton(fixture, 'Sort ascending');

    expect(fixture.componentInstance.sortingEvents).toEqual([
      [{ id: 'quantity', desc: false }],
      [{ id: 'quantity', desc: true }],
    ]);
  });

  it('keeps pagination in mobile card mode', () => {
    const fixture = TestBed.createComponent(CardTableHostComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Showing 1-10 of 42 rows');

    clickButton(fixture, 'Page 2');

    expect(fixture.componentInstance.pageChanges).toEqual([1]);
  });
});

function getHeaderRow<T>(fixture: ComponentFixture<T>): HTMLElement {
  const row = fixture.nativeElement.querySelector('[role="row"]') as HTMLElement | null;
  expect(row).not.toBeNull();
  return row as HTMLElement;
}

function getHeaderRowOrNull<T>(fixture: ComponentFixture<T>): HTMLElement | null {
  return fixture.nativeElement.querySelector('[role="row"]') as HTMLElement | null;
}

function getBodyRows<T>(fixture: ComponentFixture<T>): HTMLElement[] {
  return Array.from(fixture.nativeElement.querySelectorAll('[role="row"]')).slice(
    1,
  ) as HTMLElement[];
}

function clickHeader<T>(fixture: ComponentFixture<T>, label: string): void {
  const buttons = Array.from(
    fixture.nativeElement.querySelectorAll('button'),
  ) as HTMLButtonElement[];
  const button = buttons.find((b) => b.textContent?.includes(label));
  expect(button).toBeTruthy();
  button?.click();
}

function clickButton<T>(fixture: ComponentFixture<T>, ariaLabel: string): void {
  const buttons = Array.from(
    fixture.nativeElement.querySelectorAll('button'),
  ) as HTMLButtonElement[];
  const button = buttons.find((b) => b.getAttribute('aria-label') === ariaLabel);
  expect(button).toBeTruthy();
  button?.click();
}
