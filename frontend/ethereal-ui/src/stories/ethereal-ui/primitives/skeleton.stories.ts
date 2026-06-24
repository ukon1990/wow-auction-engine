import { FormsModule } from '@angular/forms';
import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';

import {
  ChartPanelComponent,
  GlassPanelComponent,
  ItemStatCardComponent,
  SkeletonDirective,
  TextInputComponent,
} from '../../../public-api';

type SkeletonStoryArgs = {
  loading: boolean;
  textFieldValue: string;
};

const meta: Meta<SkeletonStoryArgs> = {
  title: 'Ethereal UI/Primitives/Skeleton',
  tags: ['autodocs'],
  decorators: [
    moduleMetadata({
      imports: [
        FormsModule,
        GlassPanelComponent,
        TextInputComponent,
        ItemStatCardComponent,
        ChartPanelComponent,
        SkeletonDirective,
      ],
    }),
  ],
  argTypes: {
    loading: {
      control: { type: 'boolean' },
      description: 'Shows the host as a skeleton when true',
    },
    textFieldValue: {
      control: { type: 'text' },
    },
  },
};

export default meta;

type Story = StoryObj<SkeletonStoryArgs>;

export const TextInput: Story = {
  args: {
    loading: true,
    textFieldValue: 'Example value',
  },
  render: (args) => ({
    props: args,
    template: `
      <div style="max-width: 26rem">
        <ee-text-input
          label="Reference"
          [(ngModel)]="textFieldValue"
          [eeSkeleton]="loading"
        ></ee-text-input>
      </div>
    `,
  }),
};

export const GlassPanel: Story = {
  args: {
    loading: true,
    textFieldValue: 'Example value',
  },
  render: (args) => ({
    props: args,
    template: `
      <div style="max-width: 42rem">
        <ee-glass-panel [eeSkeleton]="loading">
          <h2 class="ee-section-heading text-on-surface">Market summary</h2>
          <p class="ee-data text-outline">
            The panel keeps its layout while content is shown as skeleton.
          </p>
          <p class="ee-data text-outline">
            Apply the directive on the host component without extra markup.
          </p>
        </ee-glass-panel>
      </div>
    `,
  }),
};

export const ItemStatCard: Story = {
  args: {
    loading: true,
    textFieldValue: 'Example value',
  },
  render: (args) => ({
    props: args,
    template: `
      <div style="max-width: 20rem">
        <ee-item-stat-card
          label="Realm price"
          icon="payments"
          value="12,450"
          unit="g"
          caption="+2.4% vs yesterday"
          tone="primary"
          [eeSkeleton]="loading"
        />
      </div>
    `,
  }),
};

export const MixedLayout: Story = {
  args: {
    loading: true,
    textFieldValue: 'FORSC-6598',
  },
  render: (args) => ({
    props: args,
    template: `
      <div style="display: grid; gap: 1rem; max-width: 56rem">
        <ee-glass-panel [eeSkeleton]="loading">
          <div style="display: grid; gap: 1rem">
            <ee-text-input
              label="Reference"
              [(ngModel)]="textFieldValue"
            ></ee-text-input>
            <div class="grid gap-4 md:grid-cols-2">
              <ee-item-stat-card
                label="Realm price"
                icon="payments"
                value="—"
                tone="primary"
              />
              <ee-item-stat-card
                label="Quantity"
                icon="inventory_2"
                value="—"
              />
            </div>
          </div>
        </ee-glass-panel>
        <ee-chart-panel
          title="Daily market"
          rangeLabel="14 days"
          [series]="[]"
          [eeSkeleton]="loading"
        />
      </div>
    `,
  }),
};
