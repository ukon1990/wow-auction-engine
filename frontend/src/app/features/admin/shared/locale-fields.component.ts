import { ChangeDetectionStrategy, Component, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GameLocale } from '@api/generated';
import {
  GAME_LOCALE_FIELDS,
  updateGameLocaleField,
} from '@features/admin/shared/game-locale-fields';
import { TextInputComponent } from '@ui';

const standaloneModel = { standalone: true };

@Component({
  selector: 'app-locale-fields',
  imports: [FormsModule, TextInputComponent],
  template: `
    <fieldset class="grid gap-4">
      <legend class="font-semibold text-on-surface">Translations</legend>
      @for (field of fields; track field.key) {
        <ee-text-input
          [label]="field.label"
          [ngModel]="valueFor(field.key)"
          [ngModelOptions]="standaloneModel"
          (ngModelChange)="onFieldChange(field.key, $event)"
        />
      }
    </fieldset>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LocaleFieldsComponent {
  protected readonly standaloneModel = standaloneModel;
  protected readonly fields = GAME_LOCALE_FIELDS;

  readonly value = input.required<GameLocale>();
  readonly valueChange = output<GameLocale>();

  private readonly draft = signal<GameLocale>({});

  constructor() {
    effect(() => {
      this.draft.set({ ...this.value() });
    });
  }

  protected valueFor(key: keyof GameLocale): string {
    return this.draft()[key] ?? '';
  }

  protected onFieldChange(key: keyof GameLocale, nextValue: string): void {
    const updated = updateGameLocaleField(this.draft(), key, nextValue);
    this.draft.set(updated);
    this.valueChange.emit(updated);
  }
}
