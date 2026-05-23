import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DateColumnComponent } from './date-column.component';

describe('DateColumnComponent', () => {
  let component: DateColumnComponent;
  let fixture: ComponentFixture<DateColumnComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DateColumnComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DateColumnComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
