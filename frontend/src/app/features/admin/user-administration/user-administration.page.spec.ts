import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';

import { AdminUserService } from './admin-user.service';
import { UserAdministrationPage } from './user-administration.page';
import type { User } from '@api/generated';

describe('UserAdministrationPage', () => {
  let component: UserAdministrationPage;
  let fixture: ComponentFixture<UserAdministrationPage>;
  let users: ReturnType<typeof signal<User[]>>;

  beforeEach(async () => {
    users = signal<User[]>([
      {
        sub: 'charlie',
        email: 'charlie@example.com',
        email_verified: false,
        status: 'Enabled',
        lastModified: '2026-05-02',
      },
      {
        sub: 'alice',
        email: 'alice@example.com',
        email_verified: true,
        status: 'Enabled',
        lastModified: '2026-05-01',
      },
    ]);
    const serviceStub = {
      loading: signal(false),
      users,
      getAllUsers: vitest.fn().mockReturnValue(of([])),
    };

    await TestBed.configureTestingModule({
      imports: [UserAdministrationPage],
      providers: [{ provide: AdminUserService, useValue: serviceStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(UserAdministrationPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('sorts users locally without page-level sorting bindings', () => {
    fixture.detectChanges();

    clickHeader(fixture, 'Email');
    fixture.detectChanges();

    const bodyRows = Array.from(fixture.nativeElement.querySelectorAll('[role="row"]')).slice(
      1,
    ) as HTMLElement[];

    expect(bodyRows[0].textContent).toContain('alice@example.com');
    expect(bodyRows[1].textContent).toContain('charlie@example.com');
  });
});

function clickHeader(fixture: ComponentFixture<UserAdministrationPage>, label: string): void {
  const buttons = Array.from(
    fixture.nativeElement.querySelectorAll('button'),
  ) as HTMLButtonElement[];
  const button = buttons.find((b) => b.textContent?.includes(label));
  expect(button).toBeTruthy();
  button?.click();
}
