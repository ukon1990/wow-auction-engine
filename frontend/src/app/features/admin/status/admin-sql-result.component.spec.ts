import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AdminSqlResult } from '@api/generated';
import { AdminSqlResultComponent } from './admin-sql-result.component';

describe('AdminSqlResultComponent', () => {
  let fixture: ComponentFixture<AdminSqlResultComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminSqlResultComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminSqlResultComponent);
  });

  it('paginates tabular sql results', () => {
    fixture.componentRef.setInput('result', resultWithRows(60));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('row-1');
    expect(fixture.nativeElement.textContent).toContain('row-50');
    expect(fixture.nativeElement.textContent).not.toContain('row-51');
    expect(fixture.nativeElement.textContent).toContain('Showing 1-50 of 60 rows');

    const nextButton = fixture.nativeElement.querySelector(
      'button[aria-label="Next page"]',
    ) as HTMLButtonElement;
    nextButton.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('row-1');
    expect(fixture.nativeElement.textContent).toContain('row-51');
    expect(fixture.nativeElement.textContent).toContain('row-60');
    expect(fixture.nativeElement.textContent).toContain('Showing 51-60 of 60 rows');
  });

  it('updates page size and resets to the first page', () => {
    fixture.componentRef.setInput('result', resultWithRows(60));
    fixture.detectChanges();

    const nextButton = fixture.nativeElement.querySelector(
      'button[aria-label="Next page"]',
    ) as HTMLButtonElement;
    nextButton.click();
    fixture.detectChanges();

    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    select.value = '25';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('row-1');
    expect(fixture.nativeElement.textContent).toContain('row-25');
    expect(fixture.nativeElement.textContent).not.toContain('row-26');
    expect(fixture.nativeElement.textContent).toContain('Showing 1-25 of 60 rows');
  });
});

function resultWithRows(count: number): AdminSqlResult {
  return {
    mode: AdminSqlResult.ModeEnum.Query,
    effectiveSql: 'SELECT id FROM item LIMIT 500',
    columns: ['id'],
    rows: Array.from({ length: count }, (_, index) => [`row-${index + 1}`]),
    rowCount: count,
    truncated: false,
    durationMs: 3,
  };
}
