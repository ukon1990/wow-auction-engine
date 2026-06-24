import { Directive, booleanAttribute, input } from '@angular/core';

@Directive({
  selector: '[eeSkeleton]',
  host: {
    '[class.ee-skeleton]': 'eeSkeleton()',
    '[attr.aria-busy]': 'eeSkeleton() ? "true" : null',
    '[attr.inert]': 'eeSkeleton() ? "" : null',
  },
})
export class SkeletonDirective {
  readonly eeSkeleton = input(true, { transform: booleanAttribute });
}
