import { ChangeDetectorRef, Component, ViewEncapsulation, inject } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import { BadgeComponent } from '../components/primitives/badge.component';
import { SkeletonDirective } from './skeleton.directive';

@Component({
  template: `
    <div class="host" [eeSkeleton]="loading">
      <button>Submit</button>
    </div>
    <div class="label-host" eeSkeleton>
      <label class="ee-label">Visible label</label>
    </div>
    <div class="tag-host" eeSkeleton>
      <ee-badge quality="rare">Status</ee-badge>
    </div>
    <div class="container-host" eeSkeleton>
      <div class="layout-container">
        <span>Nested text</span>
      </div>
    </div>
    <div class="static-host" eeSkeleton>Static skeleton</div>
  `,
  styleUrl: './skeleton.css',
  encapsulation: ViewEncapsulation.None,
  imports: [BadgeComponent, SkeletonDirective],
})
class SkeletonDirectiveHostComponent {
  private _loading = false;
  private readonly cdr = inject(ChangeDetectorRef);

  get loading(): boolean {
    return this._loading;
  }

  set loading(value: boolean) {
    if (this._loading !== value) {
      this._loading = value;
      this.cdr.markForCheck();
    }
  }
}

describe('SkeletonDirective', () => {
  let fixture: ComponentFixture<SkeletonDirectiveHostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SkeletonDirectiveHostComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SkeletonDirectiveHostComponent);
    fixture.detectChanges();
  });

  it('should not add skeleton state when loading is false', () => {
    const host: HTMLElement = fixture.nativeElement.querySelector('.host')!;
    expect(host.classList.contains('ee-skeleton')).toBeFalsy();
    expect(host.getAttribute('aria-busy')).toBeNull();
    expect(host.hasAttribute('inert')).toBeFalsy();
  });

  it('should add skeleton state when loading is true', () => {
    fixture.componentInstance.loading = true;
    fixture.detectChanges();
    fixture.detectChanges();

    const host: HTMLElement = fixture.nativeElement.querySelector('.host')!;
    expect(host.classList.contains('ee-skeleton')).toBeTruthy();
    expect(host.getAttribute('aria-busy')).toBe('true');
    expect(host.hasAttribute('inert')).toBeTruthy();
  });

  it('should remove skeleton state when loading changes back to false', () => {
    fixture.componentInstance.loading = true;
    fixture.detectChanges();
    fixture.detectChanges();

    fixture.componentInstance.loading = false;
    fixture.detectChanges();
    fixture.detectChanges();

    const host: HTMLElement = fixture.nativeElement.querySelector('.host')!;
    expect(host.classList.contains('ee-skeleton')).toBeFalsy();
    expect(host.getAttribute('aria-busy')).toBeNull();
    expect(host.hasAttribute('inert')).toBeFalsy();
  });

  it('should support static attribute usage', () => {
    const host: HTMLElement = fixture.nativeElement.querySelector('.static-host')!;
    expect(host.classList.contains('ee-skeleton')).toBeTruthy();
    expect(host.getAttribute('aria-busy')).toBe('true');
    expect(host.hasAttribute('inert')).toBeTruthy();
  });

  it('should not inject skeleton styles from the directive', () => {
    expect(document.getElementById('ee-skeleton-styles')).toBeNull();
  });

  it('should render form field labels as skeleton surfaces while loading', () => {
    const label: HTMLElement = fixture.nativeElement.querySelector('.label-host label')!;
    const color = getComputedStyle(label).color;
    expect(color === 'transparent' || color === 'rgba(0, 0, 0, 0)').toBe(true);
    expect(getComputedStyle(label).backgroundImage).toContain('linear-gradient');
  });

  it('should render badges as skeleton surfaces while loading', () => {
    const badge: HTMLElement = fixture.nativeElement.querySelector('.tag-host .ee-label')!;
    expect(getComputedStyle(badge).backgroundImage).toContain('linear-gradient');
  });

  it('should not render skeleton surfaces on layout containers', () => {
    const host: HTMLElement = fixture.nativeElement.querySelector('.container-host')!;
    const container: HTMLElement = fixture.nativeElement.querySelector('.layout-container')!;
    const nestedText: HTMLElement = container.querySelector('span')!;

    expect(getComputedStyle(host).backgroundImage).toBe('');
    expect(getComputedStyle(container).backgroundImage).toBe('');
    expect(getComputedStyle(nestedText).backgroundImage).toContain('linear-gradient');
  });
});
