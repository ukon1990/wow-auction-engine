AuctionHelperProfessionsDB = {
  ["addonVersion"] = "1.2.3",
  ["characters"] = {
    ["Player-Realm"] = {
      ["meta"] = { ["name"] = "Player", ["realm"] = "Realm", ["guid"] = "Player-1" },
      ["professions"] = {
        ["Blacksmithing"] = {
          ["skillLineID"] = 2872,
          ["currentLevelName"] = "Khaz Algar Blacksmithing",
          ["skillLevel"] = 100,
          ["recipes"] = {
            ["450216"] = {
              ["skillLineAbilityID"] = 12345,
              ["info"] = {
                ["recipeID"] = 450216,
                ["name"] = "Charged Claymore",
                ["categoryID"] = 1900,
                ["learned"] = true,
                ["supportsQualities"] = true,
                ["hasSingleItemOutput"] = true,
              },
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
                  {
                    ["slotIndex"] = 1,
                    ["dataSlotIndex"] = 1,
                    ["reagentType"] = "BASIC",
                    ["quantityRequired"] = 3,
                    ["reagents"] = {
                      { ["itemID"] = 210221, ["quality"] = 1 },
                      { ["itemID"] = 210222, ["quality"] = 2 },
                    },
                  },
                },
              },
              ["crafting"] = {
                ["baseDifficulty"] = 300,
                ["baseSkill"] = 100,
                ["bonusSkill"] = 25,
                ["requiredReagentSkillDelta"] = 40,
                ["maxQualityRequiredReagents"] = {
                  { ["dataSlotIndex"] = 1, ["quantity"] = 3, ["reagent"] = { ["itemID"] = 210222 } },
                },
                ["qualityThresholds"] = { 100, 200, 300 },
                ["base"] = {
                  ["lowerSkillThreshold"] = 200,
                  ["upperSkillTreshold"] = 300,
                },
              },
            },
            ["999"] = {
              ["info"] = { ["recipeID"] = 999, ["name"] = "Incomplete", ["supportsQualities"] = true, ["hasSingleItemOutput"] = true },
              ["outputs"] = {},
              ["schematic"] = {},
            },
            ["1000"] = {
              ["info"] = {
                ["recipeID"] = 1000,
                ["name"] = "Gathering recipe",
                ["learned"] = true,
                ["supportsQualities"] = false,
                ["isGatheringRecipe"] = true,
                ["hasSingleItemOutput"] = false,
              },
              ["outputs"] = { ["recipeType"] = 4 },
              ["schematic"] = { ["recipeType"] = 4, ["hasCraftingOperationInfo"] = false },
            },
          },
        },
      },
    },
  },
}
