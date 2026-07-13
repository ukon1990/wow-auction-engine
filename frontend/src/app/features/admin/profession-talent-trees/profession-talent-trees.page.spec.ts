import {
  MAX_FILE_SIZE_BYTES,
  MAX_TOTAL_FILE_SIZE_BYTES,
  selectedFilesForUpload,
  selectSavedVariablesFiles,
} from './profession-talent-trees.page';

function file(name: string, size = 1): File {
  return { name, size } as File;
}

describe('SavedVariables folder selection', () => {
  it('keeps only recognized files and uploads only those files', () => {
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
    expect(selectedFilesForUpload(selection.files)).toEqual([auctionHelper, professions]);
  });

  it('allows a missing source and uploads the recognized source that was selected', () => {
    const professions = file('AuctionHelper_Professions.lua');
    const selection = selectSavedVariablesFiles([professions]);

    expect(selection.error).toBeNull();
    expect(selection.files.auctionHelper).toBeNull();
    expect(selection.files.professions).toBe(professions);
    expect(selectedFilesForUpload(selection.files)).toEqual([professions]);
  });

  it('rejects duplicate recognized files', () => {
    const selection = selectSavedVariablesFiles([
      file('AuctionHelper.lua'),
      file('auctionhelper.lua'),
    ]);

    expect(selection.error).toBe('duplicate');
    expect(selectedFilesForUpload(selection.files)).toEqual([]);
  });

  it('rejects files that exceed the per-file upload limit', () => {
    const selection = selectSavedVariablesFiles([
      file('AuctionHelper_Professions.lua', MAX_FILE_SIZE_BYTES + 1),
    ]);

    expect(selection.error).toBe('fileSize');
    expect(selectedFilesForUpload(selection.files)).toEqual([]);
  });

  it('allows two files at the combined upload limit', () => {
    const selection = selectSavedVariablesFiles([
      file('AuctionHelper.lua', MAX_TOTAL_FILE_SIZE_BYTES / 2),
      file('AuctionHelper_Professions.lua', MAX_TOTAL_FILE_SIZE_BYTES / 2),
    ]);

    expect(selection.error).toBeNull();
    expect(selectedFilesForUpload(selection.files)).toHaveLength(2);
  });
});
