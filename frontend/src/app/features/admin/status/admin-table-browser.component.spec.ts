import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdminTableBrowserComponent } from './admin-table-browser.component';
import { AdminSqlService } from './admin-sql.service';

describe('AdminTableBrowserComponent', () => {
  let fixture: ComponentFixture<AdminTableBrowserComponent>;
  let service: { getMetadata: ReturnType<typeof vitest.fn> };

  beforeEach(async () => {
    service = {
      getMetadata: vitest.fn().mockReturnValue(
        of({
          tables: [
            {
              name: 'auction',
              engine: 'InnoDB',
              tableRows: 12,
              columns: [
                {
                  name: 'id',
                  dataType: 'bigint',
                  columnType: 'bigint(20)',
                  nullable: false,
                  ordinalPosition: 1,
                },
              ],
              indexes: [
                {
                  name: 'PRIMARY',
                  unique: true,
                  columns: ['id'],
                },
              ],
            },
          ],
        }),
      ),
    };

    await TestBed.configureTestingModule({
      imports: [AdminTableBrowserComponent],
      providers: [{ provide: AdminSqlService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminTableBrowserComponent);
    fixture.detectChanges();
  });

  it('loads table metadata when opened', () => {
    clickButton('Tables');

    expect(service.getMetadata).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('auction');
    expect(fixture.nativeElement.textContent).toContain('InnoDB');
  });

  function clickButton(label: string): void {
    const button = [...fixture.nativeElement.querySelectorAll('button')].find(
      (candidate: HTMLButtonElement) => candidate.textContent?.includes(label),
    ) as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
  }
});
