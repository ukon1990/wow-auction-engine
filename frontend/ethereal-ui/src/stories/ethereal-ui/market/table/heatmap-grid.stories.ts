import type { Meta, StoryObj } from '@storybook/angular';

import { HeatmapGridComponent, type HeatmapCell } from '../../../../lib/components/market/heatmap-grid.component';

const cells: HeatmapCell[] = Array.from({ length: 7 }, (_, row) =>
  Array.from({ length: 24 }, (_, col) => ({
    row,
    col,
    value: Math.round((Math.sin(row * 0.9) + Math.cos(col * 0.35)) * 500 + 800),
    label: `${Math.round((Math.sin(row * 0.9) + Math.cos(col * 0.35)) * 500 + 800)} copper`,
  })),
).flat();

const meta: Meta<HeatmapGridComponent> = {
  title: 'Ethereal UI/Market/Table/Heatmap Grid',
  component: HeatmapGridComponent,
  args: {
    title: 'Crafting profit heatmap',
    rangeLabel: '14 days',
    description: 'Average profit by day and hour.',
    rowLabels: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
    columnLabels: Array.from({ length: 24 }, (_, h) => String(h).padStart(2, '0')),
    cells,
  },
};

export default meta;
type Story = StoryObj<HeatmapGridComponent>;

export const Default: Story = {};
