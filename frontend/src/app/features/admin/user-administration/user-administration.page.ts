import { Component, computed, inject, signal } from '@angular/core';
import { PageFrameComponent, TableComponent } from '@ui';
import { AdminUserService } from '@features/admin/user-administration/admin-user.service';
import { firstValueFrom } from 'rxjs';
import { GRID_ROW } from '@features/market-browser/market-browser-table.columns';
import { createUserColumns } from '@features/admin/user-administration/user-administration-table.columns';
import { User } from '@api/generated';

// helps determine the class of the rows
export function RowClass(_: User): string {
  return GRID_ROW;
}
@Component({
  selector: 'app-user-administration.page',
  imports: [PageFrameComponent, TableComponent],
  templateUrl: './user-administration.page.html',
  styleUrl: './user-administration.page.css',
})
export class UserAdministrationPage {
  private readonly service = inject(AdminUserService);
  readonly loading = this.service.loading.asReadonly();
  readonly users = this.service.users.asReadonly();
  readonly columns = signal(createUserColumns());
  protected readonly rowClass = RowClass;
  protected readonly gridTemplate = computed(() =>
    this.columns()
      .map((_) => 'minmax(14rem, 2fr)')
      .join(', '),
  );
  constructor() {
    firstValueFrom(this.service.getAllUsers());
  }
}
