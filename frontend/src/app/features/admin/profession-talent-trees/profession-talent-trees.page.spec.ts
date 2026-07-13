import {
  MAX_FILE_SIZE_BYTES,
  MAX_TOTAL_FILE_SIZE_BYTES,
  ProfessionTalentTreesPage,
  canProcessSelection,
  buildProfessionRecipeOverview,
  savedVariablesInspectionError,
  selectedFilesForProcessing,
  selectSavedVariablesFiles,
} from './profession-talent-trees.page';
import { TestBed } from '@angular/core/testing';
import { AdminApiService } from '@api/generated';
import { ToastService } from '@core/services/toast.service';
import { of, throwError } from 'rxjs';

function file(name: string, size = 1): File {
  return { name, size } as File;
}

describe('SavedVariables folder selection', () => {
  it('reports submission success and failure through toasts', async () => {
    const toast = { success: vi.fn(), error: vi.fn() };
    const api = { inspectNormalizedAuctionHelperProfessionData: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ProfessionTalentTreesPage],
      providers: [
        { provide: AdminApiService, useValue: api },
        { provide: ToastService, useValue: toast },
      ],
    }).overrideComponent(ProfessionTalentTreesPage, { set: { template: '' } });
    const component = TestBed.createComponent(ProfessionTalentTreesPage)
      .componentInstance as unknown as {
      preview: { set(value: { payload: ReturnType<typeof professionPreview>['payload'] }): void };
      submitNormalizedData(): Promise<void>;
    };
    component.preview.set({ payload: professionPreview().payload });
    api.inspectNormalizedAuctionHelperProfessionData.mockReturnValue(
      of({ charactersFound: 1, professionsFound: 1, recipesFound: 1, diagnostics: [] }),
    );

    await component.submitNormalizedData();
    expect(toast.success).toHaveBeenCalledOnce();

    api.inspectNormalizedAuctionHelperProfessionData.mockReturnValue(
      throwError(() => new Error('network failure')),
    );
    await component.submitNormalizedData();
    expect(toast.error).toHaveBeenCalledOnce();
  });

  it('aggregates professions and de-duplicates recipes across characters', () => {
    expect(buildProfessionRecipeOverview(professionPreview())).toMatchObject([
      { professionId: 164, characterCount: 2, recipeCount: 1 },
    ]);
  });

  it('filters and paginates one active profession at a time', () => {
    const toast = { success: vi.fn(), error: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ProfessionTalentTreesPage],
      providers: [
        { provide: AdminApiService, useValue: {} },
        { provide: ToastService, useValue: toast },
      ],
    }).overrideComponent(ProfessionTalentTreesPage, { set: { template: '' } });
    const component = TestBed.createComponent(ProfessionTalentTreesPage)
      .componentInstance as unknown as {
      preview: { set(value: ReturnType<typeof professionPreview>): void };
      activeProfessionId: { set(value: number): void };
      recipeSearch: { set(value: string): void };
      recipePage: { set(value: number): void };
      visibleRecipes(): readonly { recipeId: number }[];
      recipePagination(): { page: number; totalItems: number; totalPages: number };
      selectProfession(professionId: number): void;
      onProfessionTabKeydown(event: KeyboardEvent, index: number): void;
    };
    const preview = professionPreview();
    preview.payload.characters[0].professions[0].recipes = Array.from(
      { length: 61 },
      (_, index) => ({
        recipeId: index + 1,
        name: index === 60 ? 'Special Hammer' : `Recipe ${index + 1}`,
        learned: true,
        qualityOutputItemIds: [],
        qualityThresholds: [],
        reagentSlots: [],
        maxQualityRequiredReagents: [],
      }),
    );
    preview.payload.characters[0].professions.push({
      ...preview.payload.characters[0].professions[0],
      professionId: 333,
      skillLineId: 333,
      name: 'Enchanting',
      recipes: [],
    });
    component.preview.set(preview);
    component.activeProfessionId.set(164);

    expect(component.visibleRecipes()).toHaveLength(50);
    expect(component.recipePagination()).toMatchObject({ totalItems: 61, totalPages: 2 });

    component.recipePage.set(1);
    expect(component.visibleRecipes()).toHaveLength(11);

    component.recipeSearch.set('special');
    expect(component.recipePagination()).toMatchObject({ page: 0, totalItems: 1, totalPages: 1 });
    expect(component.visibleRecipes().map((recipe) => recipe.recipeId)).toEqual([61]);

    component.selectProfession(164);
    expect(component.recipePagination().page).toBe(0);

    const preventDefault = vi.fn();
    component.onProfessionTabKeydown(
      {
        key: 'ArrowRight',
        preventDefault,
        currentTarget: { parentElement: null },
      } as unknown as KeyboardEvent,
      0,
    );
    expect(preventDefault).toHaveBeenCalledOnce();
    expect(component.recipePagination().totalItems).toBe(0);
  });

  it('renders accessible tabs and one recipe per row or card for the active page', async () => {
    TestBed.configureTestingModule({
      imports: [ProfessionTalentTreesPage],
      providers: [
        { provide: AdminApiService, useValue: {} },
        { provide: ToastService, useValue: { success: vi.fn(), error: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(ProfessionTalentTreesPage);
    const component = fixture.componentInstance as unknown as {
      preview: { set(value: ReturnType<typeof professionPreview>): void };
      activeProfessionId: { set(value: number): void };
    };
    const preview = professionPreview();
    preview.payload.characters[0].professions[0].recipes = Array.from(
      { length: 51 },
      (_, index) => ({
        recipeId: index + 1,
        name: `Recipe ${String(index + 1).padStart(2, '0')}`,
        learned: true,
        qualityOutputItemIds: [],
        qualityThresholds: [],
        reagentSlots: [],
        maxQualityRequiredReagents: [],
      }),
    );
    preview.payload.characters[0].professions.push({
      ...preview.payload.characters[0].professions[0],
      professionId: 333,
      skillLineId: 333,
      name: 'Enchanting',
      recipes: [],
    });
    component.preview.set(preview);
    component.activeProfessionId.set(164);
    await fixture.whenStable();

    const root = fixture.nativeElement as HTMLElement;
    const tabs = [...root.querySelectorAll<HTMLButtonElement>('[role="tab"]')];
    const panel = root.querySelector<HTMLElement>('[role="tabpanel"]');
    expect(tabs).toHaveLength(2);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
    expect(tabs[0].tabIndex).toBe(0);
    expect(tabs[1].getAttribute('aria-selected')).toBe('false');
    expect(tabs[1].tabIndex).toBe(-1);
    expect(tabs.every((tab) => tab.getAttribute('aria-controls') === panel?.id)).toBe(true);
    expect(panel?.getAttribute('aria-labelledby')).toBe(tabs[0].id);

    const tableRows = [...root.querySelectorAll<HTMLElement>('ee-table [role="row"]')].filter(
      (row) => !row.querySelector('[role="columnheader"]'),
    );
    expect(tableRows).toHaveLength(50);
    expect(tableRows.every((row) => row.textContent?.match(/Recipe \d+/g)?.length === 1)).toBe(
      true,
    );

    const cardsButton = [...root.querySelectorAll<HTMLButtonElement>('button')].find(
      (button) => button.textContent?.trim() === 'Cards',
    );
    cardsButton?.click();
    await fixture.whenStable();
    const cards = root.querySelectorAll('ee-table article');
    expect(cards).toHaveLength(50);
    expect([...cards].every((card) => card.textContent?.match(/Recipe \d+/g)?.length === 1)).toBe(
      true,
    );

    tabs[0].focus();
    tabs[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    await fixture.whenStable();
    expect(document.activeElement).toBe(tabs[1]);
    expect(tabs[1].getAttribute('aria-selected')).toBe('true');
    expect(panel?.getAttribute('aria-labelledby')).toBe(tabs[1].id);
  });

  function professionPreview() {
    const profession = {
      professionId: 164,
      skillLineId: 164,
      name: 'Blacksmithing',
      recipes: [
        {
          recipeId: 1,
          name: 'Hammer',
          learned: true,
          qualityOutputItemIds: [],
          qualityThresholds: [],
          reagentSlots: [],
          maxQualityRequiredReagents: [],
        },
      ],
    };
    return {
      charactersFound: 2,
      professionsFound: 2,
      recipesFound: 2,
      diagnostics: [],
      payload: {
        contractVersion: 1 as const,
        source: { addon: 'AuctionHelper' as const, processorVersion: '1', files: [] },
        characters: [
          {
            characterKey: 'eu:a',
            name: 'A',
            realm: 'Realm',
            region: 'eu',
            professions: [profession],
          },
          {
            characterKey: 'eu:b',
            name: 'B',
            realm: 'Realm',
            region: 'eu',
            professions: [profession],
          },
        ],
      },
    };
  }

  it('shows the backend response detail instead of a generic upload error', async () => {
    const { HttpErrorResponse } = await import('@angular/common/http');
    expect(
      savedVariablesInspectionError(
        new HttpErrorResponse({
          error: { detail: 'Each SavedVariables file must be at most 64 MiB' },
          status: 400,
        }),
      ),
    ).toBe('Each SavedVariables file must be at most 64 MiB');
  });

  it('keeps only recognized files for local processing', () => {
    const auctionHelper = file('AuctionHelper.lua');
    const professions = file('AuctionHelper_Professions.lua');
    const selection = selectSavedVariablesFiles([
      auctionHelper,
      professions,
      file('AuctionHelper_Inventory.lua'),
      file('unrelated-addon.lua'),
    ]);

    expect(selection.error).toBeNull();
    expect(selection.files.ignoredCount).toBe(2);
    expect(selection.files.auctionHelper).toBe(auctionHelper);
    expect(selection.files.professions).toBe(professions);
    expect(selectedFilesForProcessing(selection.files)).toEqual([auctionHelper, professions]);
  });

  it('allows a missing source and processes the recognized source that was selected', () => {
    const professions = file('AuctionHelper_Professions.lua');
    const selection = selectSavedVariablesFiles([professions]);

    expect(selection.error).toBeNull();
    expect(selection.files.auctionHelper).toBeNull();
    expect(selection.files.professions).toBe(professions);
    expect(selectedFilesForProcessing(selection.files)).toEqual([professions]);
    expect(canProcessSelection(selection.files, 'eu')).toBe(true);
  });

  it('waits for the required professions file or a region before automatic processing', () => {
    const talentOnly = selectSavedVariablesFiles([file('AuctionHelper.lua')]);
    const professions = selectSavedVariablesFiles([file('AuctionHelper_Professions.lua')]);

    expect(canProcessSelection(talentOnly.files, 'eu')).toBe(false);
    expect(canProcessSelection(professions.files, '')).toBe(false);
  });

  it('rejects duplicate recognized files', () => {
    const selection = selectSavedVariablesFiles([
      file('AuctionHelper.lua'),
      file('auctionhelper.lua'),
    ]);

    expect(selection.error).toBe('duplicate');
    expect(selectedFilesForProcessing(selection.files)).toEqual([]);
  });

  it('rejects files that exceed the per-file upload limit', () => {
    const selection = selectSavedVariablesFiles([
      file('AuctionHelper_Professions.lua', MAX_FILE_SIZE_BYTES + 1),
    ]);

    expect(selection.error).toBe('fileSize');
    expect(selectedFilesForProcessing(selection.files)).toEqual([]);
  });

  it('allows two files at the combined upload limit', () => {
    const selection = selectSavedVariablesFiles([
      file('AuctionHelper.lua', MAX_TOTAL_FILE_SIZE_BYTES / 2),
      file('AuctionHelper_Professions.lua', MAX_TOTAL_FILE_SIZE_BYTES / 2),
    ]);

    expect(selection.error).toBeNull();
    expect(selectedFilesForProcessing(selection.files)).toHaveLength(2);
  });
});
