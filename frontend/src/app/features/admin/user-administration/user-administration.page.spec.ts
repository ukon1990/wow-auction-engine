import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserAdministrationPage } from './user-administration.page';

describe('UserAdministrationPage', () => {
  let component: UserAdministrationPage;
  let fixture: ComponentFixture<UserAdministrationPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserAdministrationPage],
    }).compileComponents();

    fixture = TestBed.createComponent(UserAdministrationPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
