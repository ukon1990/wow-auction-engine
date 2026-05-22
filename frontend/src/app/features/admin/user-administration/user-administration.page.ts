import { Component, signal } from '@angular/core';
import { PageFrameComponent } from '@ui';

@Component({
  selector: 'app-user-administration.page',
  imports: [PageFrameComponent],
  templateUrl: './user-administration.page.html',
  styleUrl: './user-administration.page.css',
})
export class UserAdministrationPage {
  readonly loading = signal<boolean>(false);
}
