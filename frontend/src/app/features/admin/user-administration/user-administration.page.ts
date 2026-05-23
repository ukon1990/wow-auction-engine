import { Component, inject, signal } from '@angular/core';
import { PageFrameComponent, TableComponent } from '@ui';
import { AdminUserService } from '@features/admin/user-administration/admin-user.service';
import { firstValueFrom } from 'rxjs';
import { createUserColumns } from '@features/admin/user-administration/user-administration-table.columns';

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

  constructor() {
    firstValueFrom(this.service.getAllUsers());
  }
}
