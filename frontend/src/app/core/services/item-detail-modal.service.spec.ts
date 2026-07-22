import { ItemDetailModalService } from './item-detail-modal.service';

describe('ItemDetailModalService', () => {
  let service: ItemDetailModalService;

  beforeEach(() => {
    service = new ItemDetailModalService();
  });

  it('opens and closes modal state', () => {
    expect(service.state()).toBeNull();
    service.open({
      itemId: 238197,
      bonusKey: '',
      modifierKey: '',
      petSpeciesId: -1,
      scope: 'commodity',
      recipeId: 52669,
    });
    expect(service.state()).toEqual({
      itemId: 238197,
      bonusKey: '',
      modifierKey: '',
      petSpeciesId: -1,
      scope: 'commodity',
      recipeId: 52669,
    });
    service.close();
    expect(service.state()).toBeNull();
  });

  it('updates scope while open', () => {
    service.open({ itemId: 1 });
    service.updateScope('commodity');
    expect(service.state()?.scope).toBe('commodity');
  });
});
