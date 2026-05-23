import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PaginationComponent, PaginationState } from './pagination.component';

@Component({
  imports: [PaginationComponent],
  template: `
    <ee-pagination
      [emptySummary]="emptySummary"
      [loading]="loading()"
      [loadingSummary]="loadingSummary"
      [pageState]="pageState()"
      [rowLabel]="rowLabel"
      [windowSize]="windowSize"
      (pageChange)="pageChanges.push($event)"
    />
  `,
})
class PaginationHostComponent {
  readonly pageState = signal<PaginationState | undefined>({
    page: 5,
    pageSize: 10,
    totalItems: 200,
    totalPages: 20,
  });
  readonly loading = signal(false);
  readonly loadingSummary = 'Loading market items...';
  readonly emptySummary = 'No market items available.';
  readonly rowLabel = 'rows';
  readonly windowSize = 5;
  readonly pageChanges: number[] = [];
}

describe('PaginationComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaginationHostComponent],
    }).compileComponents();
  });

  it('shows the formatted visible row range and total count', () => {
    const fixture = TestBed.createComponent(PaginationHostComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Showing 51-60 of 200 rows');
  });

  it('shows the loading summary while loading', () => {
    const fixture = TestBed.createComponent(PaginationHostComponent);
    fixture.componentInstance.loading.set(true);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Loading market items...');
  });

  it('shows the empty summary when there are no rows', () => {
    const fixture = TestBed.createComponent(PaginationHostComponent);
    fixture.componentInstance.pageState.set({
      page: 0,
      pageSize: 10,
      totalItems: 0,
      totalPages: 0,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No market items available.');
  });

  it('renders a compact numbered window with ellipses', () => {
    const fixture = TestBed.createComponent(PaginationHostComponent);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('1');
    expect(text).toContain('5');
    expect(text).toContain('6');
    expect(text).toContain('7');
    expect(text).toContain('20');
    expect(text).toContain('...');
  });

  it('emits selected numbered pages as zero-based indexes', () => {
    const fixture = TestBed.createComponent(PaginationHostComponent);
    fixture.detectChanges();

    clickButton(fixture, 'Page 7');

    expect(fixture.componentInstance.pageChanges).toEqual([6]);
  });

  it('disables controls that cannot move to another page', () => {
    const fixture = TestBed.createComponent(PaginationHostComponent);
    fixture.componentInstance.pageState.set({
      page: 0,
      pageSize: 10,
      totalItems: 10,
      totalPages: 1,
    });
    fixture.detectChanges();

    expect(button(fixture, 'First page').disabled).toBe(true);
    expect(button(fixture, 'Previous page').disabled).toBe(true);
    expect(button(fixture, 'Next page').disabled).toBe(true);
    expect(button(fixture, 'Last page').disabled).toBe(true);
  });
});

function clickButton<T>(fixture: ComponentFixture<T>, ariaLabel: string): void {
  button(fixture, ariaLabel).click();
}

function button<T>(fixture: ComponentFixture<T>, ariaLabel: string): HTMLButtonElement {
  const buttons = Array.from(
    fixture.nativeElement.querySelectorAll('button'),
  ) as HTMLButtonElement[];
  const match = buttons.find((candidate) => candidate.getAttribute('aria-label') === ariaLabel);
  expect(match).toBeTruthy();
  return match as HTMLButtonElement;
}
