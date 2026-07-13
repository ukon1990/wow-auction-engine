import { TestBed } from '@angular/core/testing';

import { CharacterProfessionPreview } from '@api/generated';
import { CharacterProfessionPreviewStorageService } from './character-profession-preview-storage.service';

const preview: CharacterProfessionPreview = {
  region: CharacterProfessionPreview.RegionEnum.Eu,
  realmSlug: 'draenor',
  characterName: 'Geltryne',
  professions: [],
};

function createService(): CharacterProfessionPreviewStorageService {
  TestBed.resetTestingModule();
  return TestBed.inject(CharacterProfessionPreviewStorageService);
}

describe('CharacterProfessionPreviewStorageService', () => {
  beforeEach(() => localStorage.clear());

  it('returns a stored preview within the cache lifetime', () => {
    const service = createService();
    service.save(preview);

    expect(service.get('EU', 'DRAENOR', 'geltryne')).toEqual(preview);
  });

  it('does not return a preview after its cache lifetime has elapsed', () => {
    localStorage.setItem(
      'wae.character-profession-previews',
      JSON.stringify([{ preview, cachedAt: Date.now() - 25 * 60 * 60 * 1000 }]),
    );
    const service = createService();

    expect(service.get('eu', 'draenor', 'geltryne')).toBeNull();
  });

  it('rejects malformed nested profession data from storage', () => {
    localStorage.setItem(
      'wae.character-profession-previews',
      JSON.stringify([
        {
          preview: {
            ...preview,
            professions: [{ professionId: 164, professionName: 'Blacksmithing' }],
          },
          cachedAt: Date.now(),
        },
      ]),
    );
    const service = createService();

    expect(service.get('eu', 'draenor', 'geltryne')).toBeNull();
  });
});
