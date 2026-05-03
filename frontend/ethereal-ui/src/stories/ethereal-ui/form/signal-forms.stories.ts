import type { Meta, StoryObj } from '@storybook/angular';
import { JsonPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormField, FormRoot, email, form, required } from '@angular/forms/signals';

import { CheckboxInputComponent, TextInputComponent } from '../../../public-api';

@Component({
  imports: [CheckboxInputComponent, FormField, FormRoot, JsonPipe, TextInputComponent],
  template: `
    <form
      [formRoot]="registrationForm"
      class="ee-glass grid w-[560px] gap-4 rounded-lg p-inner-padding"
    >
      <ee-text-input label="Email" [formField]="registrationForm.email" />
      <ee-checkbox-input label="Accept terms" [formField]="registrationForm.acceptTerms" />
      <button
        type="button"
        class="rounded bg-primary px-4 py-2 ee-label text-on-primary disabled:opacity-50"
        [disabled]="registrationForm().invalid()"
      >
        Submit
      </button>
      <pre class="rounded border border-white/10 bg-black/50 p-4 ee-data text-xs text-outline">{{
        registrationModel() | json
      }}</pre>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class SignalFormStoryHostComponent {
  readonly registrationModel = signal({
    email: '',
    acceptTerms: false,
  });

  readonly registrationForm = form(this.registrationModel, (p) => {
    required(p.email, { message: 'Email is required' });
    email(p.email, { message: 'Enter a valid email' });
    required(p.acceptTerms, { message: 'You must accept the terms' });
  });
}

const meta: Meta<SignalFormStoryHostComponent> = {
  title: 'Ethereal UI/Form',
  component: SignalFormStoryHostComponent,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component:
          'Signal forms using Angular `form()` and `[formField]` with Ethereal custom controls implementing FormValueControl / FormCheckboxControl.',
      },
    },
  },
};

export default meta;

export const SignalForms: StoryObj<SignalFormStoryHostComponent> = {};
