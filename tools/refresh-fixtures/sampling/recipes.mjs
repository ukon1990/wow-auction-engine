function recipeStubFromReference(recipe, categoryName) {
    return {
        id: recipe.id,
        name: recipe.name ?? "unknown",
        category: categoryName,
    };
}

export function pickRecipeIds(skillTierPayload, sampleSize) {
    const categories = (skillTierPayload.categories ?? [])
        .map((category) => ({
            categoryName: category.name ?? "unknown",
            recipes: category.recipes ?? [],
        }))
        .filter((category) => category.recipes.length > 0);

    if (categories.length === 0) {
        return [];
    }

    const selected = [];
    const used = new Set();
    let index = 0;
    while (selected.length < sampleSize) {
        let addedInRound = false;
        for (const category of categories) {
            if (index >= category.recipes.length) {
                continue;
            }
            const recipe = category.recipes[index];
            if (!recipe || used.has(recipe.id)) {
                continue;
            }
            selected.push(recipeStubFromReference(recipe, category.categoryName));
            used.add(recipe.id);
            addedInRound = true;
            if (selected.length >= sampleSize) {
                break;
            }
        }
        if (!addedInRound) {
            break;
        }
        index += 1;
    }

    return selected;
}

export function pickAllRecipeIds(skillTierPayload) {
    const selected = [];
    const used = new Set();

    for (const category of skillTierPayload.categories ?? []) {
        const categoryName = category.name ?? "unknown";
        for (const recipe of category.recipes ?? []) {
            if (!recipe || !Number.isFinite(recipe.id) || used.has(recipe.id)) {
                continue;
            }
            used.add(recipe.id);
            selected.push(recipeStubFromReference(recipe, categoryName));
        }
    }

    return selected.sort((left, right) => left.id - right.id);
}

export function trimSkillTierToRecipes(skillTierPayload, selectedRecipeIds) {
    const selectedSet = new Set(selectedRecipeIds);
    const categories = (skillTierPayload.categories ?? [])
        .map((category) => {
            const recipes = (category.recipes ?? []).filter((recipe) => selectedSet.has(recipe.id));
            return {
                ...category,
                recipes,
            };
        })
        .filter((category) => (category.recipes ?? []).length > 0);

    return {
        ...skillTierPayload,
        categories,
    };
}
