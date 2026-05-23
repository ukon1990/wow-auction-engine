import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';

import { AdminUserService } from './admin-user.service';
import { UserAdministrationPage } from './user-administration.page';

describe('UserAdministrationPage', () => {
  let component: UserAdministrationPage;
  let fixture: ComponentFixture<UserAdministrationPage>;

  beforeEach(async () => {
    const serviceStub = {
      loading: signal(false),
      users: signal([]),
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
});
