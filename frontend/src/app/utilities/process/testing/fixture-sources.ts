// These strings mirror the neighboring .lua examples so Angular's browser test
// builder does not need a custom loader for non-TypeScript modules.
export const auctionHelperExportSource = `AuctionHelperLastExport = {
  ["scope"] = "profession_talents",
  ["generatedAt"] = "2026-07-13T10:00:00Z",
  ["payload"] = "AHCBOR1:eJwDAAAAAAE=",
}`;

export const genericSavedVariablesSource = `ExampleDB = {
  enabled = true,
  count = 3,
  label = "safe",
  values = { "one", "two" },
  [42] = { nested = -7 },
}`;

export const malformedSavedVariablesSource = `BrokenDB = { ["unfinished"] =`;

export const auctionHelperProfessionsSource = `AuctionHelperProfessionsDB = {
  ["addonVersion"] = "1.2.3",
  ["characters"] = {
    ["Player-Realm"] = {
      ["meta"] = { ["name"] = "Player", ["realm"] = "Realm", ["guid"] = "Player-1" },
      ["professions"] = {
        ["Blacksmithing"] = {
          ["skillLineID"] = 2872,
          ["primarySpecializationSkillLineID"] = 2872,
          ["currentLevelName"] = "Khaz Algar Blacksmithing",
          ["skillLevel"] = 100,
          ["specializationTrees"] = {
            {
              ["configID"] = 123, ["skillLineID"] = 2872, ["expansionID"] = 10,
              ["tierName"] = "Khaz Algar Blacksmithing",
              ["tabs"] = {
                {
                  ["treeID"] = 999,
                  ["nodes"] = {
                    { ["nodeID"] = 101, ["childPathIDs"] = { 102 }, ["nodeInfo"] = { ["maxRanks"] = 30 }, ["entries"] = { { ["entryID"] = 201, ["definitionInfo"] = { ["overrideName"] = "Weaponsmithing" } } } },
                    { ["nodeID"] = 102, ["nodeInfo"] = { ["maxRanks"] = 10 }, ["entries"] = { { ["entryID"] = 202 } } },
                  },
                },
              },
            },
          },
          ["recipes"] = {
            ["450216"] = {
              ["skillLineAbilityID"] = 12345,
              ["info"] = { ["recipeID"] = 450216, ["name"] = "Charged Claymore", ["categoryID"] = 1900, ["learned"] = true, ["supportsQualities"] = true, ["hasSingleItemOutput"] = true },
              ["outputs"] = {
                ["raw"] = { ["itemID"] = 222437 },
                ["qualityVariants"] = {
                  { ["qualityIndex"] = 1, ["itemID"] = 222437 },
                  { ["qualityIndex"] = 2, ["itemID"] = 222438 },
                },
              },
              ["schematic"] = {
                ["recipeType"] = 1,
                ["reagentSlotSchematics"] = {
                  { ["slotIndex"] = 1, ["dataSlotIndex"] = 1, ["reagentType"] = "BASIC", ["quantityRequired"] = 3,
                    ["reagents"] = { { ["itemID"] = 210221, ["quality"] = 1 }, { ["itemID"] = 210222, ["quality"] = 2 } } },
                },
              },
              ["crafting"] = {
                ["baseDifficulty"] = 300, ["baseSkill"] = 100, ["bonusSkill"] = 25,
                ["requiredReagentSkillDelta"] = 40,
                ["maxQualityRequiredReagents"] = { { ["dataSlotIndex"] = 1, ["quantity"] = 3, ["reagent"] = { ["itemID"] = 210222 } } },
                ["qualityThresholds"] = { 100, 200, 300 },
                ["base"] = { ["lowerSkillThreshold"] = 200, ["upperSkillTreshold"] = 300 },
              },
            },
            ["999"] = {
              ["info"] = { ["recipeID"] = 999, ["name"] = "Incomplete", ["supportsQualities"] = true, ["hasSingleItemOutput"] = true },
              ["outputs"] = {}, ["schematic"] = {},
            },
            ["1000"] = {
              ["info"] = { ["recipeID"] = 1000, ["name"] = "Gathering recipe", ["learned"] = true, ["supportsQualities"] = false, ["isGatheringRecipe"] = true, ["hasSingleItemOutput"] = false },
              ["outputs"] = { ["recipeType"] = 4 },
              ["schematic"] = { ["recipeType"] = 4, ["hasCraftingOperationInfo"] = false },
            },
          },
        },
      },
    },
  },
}`;
