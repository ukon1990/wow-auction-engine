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

  it('sorts and deduplicates quality options by display quality', () => {
    const options = mapQualityFilterOptions(filter, [
      { id: '5', label: 'Legendary', parentId: null, qualityType: 'LEGENDARY' },
      { id: '4', label: 'Epic', parentId: null, qualityType: 'EPIC' },
      { id: '3', label: 'Rare', parentId: null, qualityType: 'RARE' },
      { id: '2', label: 'Uncommon', parentId: null, qualityType: 'UNCOMMON' },
      { id: '9', label: 'Poor', parentId: null, qualityType: 'POOR' },
      { id: '1', label: 'Common', parentId: null, qualityType: 'COMMON' },
      { id: '8', label: 'Gewoehnlich', parentId: null, qualityType: 'COMMON' },
      { id: '6', label: 'Artifact', parentId: null, qualityType: 'ARTIFACT' },
    ]);

    expect(options.map((option) => option.qualityType)).toEqual([
      'COMMON',
      'UNCOMMON',
      'RARE',
      'EPIC',
      'LEGENDARY',
      'ARTIFACT',
    ]);
  });
});
