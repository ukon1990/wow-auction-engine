import { AuctionMarketFilter } from '@api/generated';
import { mapQualityFilterOptions } from './quality-filter-options';

describe('quality-filter-options', () => {
  const filter: AuctionMarketFilter = {
    id: 'qualityIds',
    label: 'Quality',
    type: AuctionMarketFilter.TypeEnum.MultiSelect,
    min: null,
    max: null,
  };

  it('sorts and deduplicates quality options by quality type', () => {
    const options = mapQualityFilterOptions(filter, [
      { id: '5', label: 'Legendary', parentId: null, qualityType: 'LEGENDARY' },
      { id: '2', label: 'Uncommon', parentId: null, qualityType: 'UNCOMMON' },
      { id: '9', label: 'Poor', parentId: null, qualityType: 'POOR' },
      { id: '1', label: 'Common', parentId: null, qualityType: 'COMMON' },
      { id: '8', label: 'Gewoehnlich', parentId: null, qualityType: 'COMMON' },
    ]);

    expect(options.map((option) => option.qualityType)).toEqual([
      'POOR',
      'COMMON',
      'UNCOMMON',
      'LEGENDARY',
    ]);
  });
});
