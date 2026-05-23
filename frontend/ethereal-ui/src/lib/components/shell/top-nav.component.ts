import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  HostListener,
  inject,
  input,
  OnDestroy,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Params, RouterLink, RouterLinkActive } from '@angular/router';

import { CharacterSummary, LocaleOption, NavItem } from '../../models/ui-models';
import { IconButtonComponent } from '../primitives/icon-button.component';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';
import { TopNavDropdownItemComponent } from './top-nav-dropdown-item.component';
import { TopNavItemComponent } from './top-nav-item.component';

@Component({
  selector: 'ee-top-nav',
  imports: [
    IconButtonComponent,
    NgTemplateOutlet,
    RouterLinkActive,
    SymbolIconComponent,
    RouterLink,
    TopNavDropdownItemComponent,
    TopNavItemComponent,
  ],
  templateUrl: './top-nav.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopNavComponent implements OnDestroy {
  private static readonly FIT_BUFFER_PX = 12;
  private static readonly MIN_DESKTOP_WIDTH_PX = 768;

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly header = viewChild<ElementRef<HTMLElement>>('header');
  private readonly leftCluster = viewChild<ElementRef<HTMLElement>>('leftCluster');
  private readonly brandBlock = viewChild<ElementRef<HTMLElement>>('brandBlock');
  private readonly actionCluster = viewChild<ElementRef<HTMLElement>>('actionCluster');
  private readonly measureNav = viewChild<ElementRef<HTMLElement>>('measureNav');

  readonly items = input.required<readonly NavItem[]>();
  readonly activeId = input.required<string>();
  readonly character = input.required<CharacterSummary>();
  readonly accountRouterLink = input<string | readonly unknown[]>('/login');
  readonly accountQueryParams = input<Params | null>(null);
  readonly accountLabel = input('Account');
  readonly localeOptions = input<readonly LocaleOption[]>([]);
  readonly activeLocale = input('en');
  readonly mobileDrawerOpen = input(false);
  readonly navSelected = output<string>();
  readonly localeSelected = output<string>();
  readonly toggleMobileDrawer = output<void>();
  readonly subText = input<string>('');

  protected readonly openDropdownId = signal<string | null>(null);
  protected readonly useMobileNavigation = signal(true);

  private resizeObserver: ResizeObserver | null = null;
  private measureFrame = 0;

  constructor() {
    afterNextRender(() => {
      this.resizeObserver = new ResizeObserver(() => {
        this.scheduleNavigationMeasurement();
      });
      for (const element of this.observedElements()) {
        this.resizeObserver.observe(element);
      }
      this.scheduleNavigationMeasurement();
    });

    effect(() => {
      this.items();
      this.localeOptions();
      this.activeLocale();
      this.subText();
      this.accountLabel();
      this.scheduleNavigationMeasurement();
    });
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: MouseEvent): void {
    const target = event.target;
    if (target instanceof Node && this.host.nativeElement.contains(target)) {
      return;
    }
    this.closeDropdown();
  }

  @HostListener('document:keydown.escape')
  protected onDocumentEscape(): void {
    this.closeDropdown();
  }

  @HostListener('window:resize')
  protected onWindowResize(): void {
    this.scheduleNavigationMeasurement();
  }

  protected onPrimaryButton(id: string): void {
    this.navSelected.emit(id);
  }

  protected hasChildren(item: NavItem): boolean {
    return Boolean(item.children?.length);
  }

  protected isDropdownOpen(id: string): boolean {
    return this.openDropdownId() === id;
  }

  protected toggleDropdown(id: string): void {
    this.openDropdownId.update((openId) => (openId === id ? null : id));
  }

  protected closeDropdown(): void {
    this.openDropdownId.set(null);
  }

  protected onDropdownButton(id: string): void {
    this.navSelected.emit(id);
    this.closeDropdown();
  }

  protected onMobilePrimaryButton(id: string): void {
    this.navSelected.emit(id);
    this.closeMobileNavigation();
  }

  protected onMobileLinkSelected(): void {
    this.closeMobileNavigation();
  }

  protected onLocaleChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    if (select?.value) {
      this.localeSelected.emit(select.value);
    }
  }

  protected onMobileLocaleChange(event: Event): void {
    this.onLocaleChange(event);
    this.closeMobileNavigation();
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    if (this.measureFrame) {
      cancelAnimationFrame(this.measureFrame);
    }
  }

  private scheduleNavigationMeasurement(): void {
    if (typeof requestAnimationFrame !== 'function') {
      return;
    }
    cancelAnimationFrame(this.measureFrame);
    this.measureFrame = requestAnimationFrame(() => {
      this.measureFrame = 0;
      this.updateNavigationMode();
    });
  }

  private updateNavigationMode(): void {
    const header = this.header()?.nativeElement;
    const leftCluster = this.leftCluster()?.nativeElement;
    const brandBlock = this.brandBlock()?.nativeElement;
    const actionCluster = this.actionCluster()?.nativeElement;
    const measureNav = this.measureNav()?.nativeElement;
    if (!header || !leftCluster || !brandBlock || !actionCluster || !measureNav) {
      return;
    }

    const headerStyles = getComputedStyle(header);
    const leftStyles = getComputedStyle(leftCluster);
    const headerHorizontalPadding =
      this.cssPixels(headerStyles.paddingLeft) + this.cssPixels(headerStyles.paddingRight);
    const headerGap = this.cssPixels(headerStyles.columnGap || headerStyles.gap);
    const leftGap = this.cssPixels(leftStyles.columnGap || leftStyles.gap);

    const availableWidth =
      header.clientWidth -
      headerHorizontalPadding -
      (this.useMobileNavigation() ? 0 : actionCluster.offsetWidth) -
      headerGap -
      TopNavComponent.FIT_BUFFER_PX;
    const requiredWidth = brandBlock.scrollWidth + measureNav.scrollWidth + leftGap;
    const nextUseMobileNavigation =
      header.clientWidth < TopNavComponent.MIN_DESKTOP_WIDTH_PX || requiredWidth > availableWidth;

    if (this.useMobileNavigation() !== nextUseMobileNavigation) {
      this.useMobileNavigation.set(nextUseMobileNavigation);
      if (nextUseMobileNavigation) {
        this.closeDropdown();
      } else if (this.mobileDrawerOpen()) {
        this.toggleMobileDrawer.emit();
      }
    }
  }

  protected closeMobileNavigation(): void {
    if (this.mobileDrawerOpen()) {
      this.toggleMobileDrawer.emit();
    }
  }

  private observedElements(): HTMLElement[] {
    return [
      this.header()?.nativeElement,
      this.leftCluster()?.nativeElement,
      this.brandBlock()?.nativeElement,
      this.actionCluster()?.nativeElement,
      this.measureNav()?.nativeElement,
    ].filter((element): element is HTMLElement => Boolean(element));
  }

  private cssPixels(value: string): number {
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
}
