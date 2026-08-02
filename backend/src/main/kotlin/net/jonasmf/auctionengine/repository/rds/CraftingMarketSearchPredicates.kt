package net.jonasmf.auctionengine.repository.rds

internal fun appendCraftingQueryPredicate(predicates: MutableList<String>, params: MutableList<Any?>, query: String?) {
    val term = query?.trim()?.takeIf(String::isNotEmpty) ?: return
    predicates += "(c.item_name LIKE ? ESCAPE '!' OR c.recipe_name LIKE ? ESCAPE '!')"
    val like = "%${term.escapeCraftingLike()}%"
    params += like
    params += like
}

internal fun appendCraftingInPredicate(predicates: MutableList<String>, params: MutableList<Any?>, column: String, values: List<Int>) {
    if (values.isEmpty()) return
    predicates += "$column IN (${values.joinToString(",") { "?" }})"
    params.addAll(values)
}

internal fun appendCraftingRangePredicates(
    predicates: MutableList<String>,
    params: MutableList<Any?>,
    request: CraftingMarketSearchRequest,
) {
    appendCraftingRangePredicate(predicates, params, "c.profit_copper", ">=", request.minProfit)
    appendCraftingRangePredicate(predicates, params, "c.profit_copper", "<=", request.maxProfit)
    appendCraftingRangePredicate(predicates, params, "c.roi_percent", ">=", request.minRoiPercent)
    appendCraftingRangePredicate(predicates, params, "c.roi_percent", "<=", request.maxRoiPercent)
    appendCraftingRangePredicate(predicates, params, "c.sale_rate", ">=", request.minSaleRatePercent?.div(100.0))
    appendCraftingRangePredicate(predicates, params, "c.sale_rate", "<=", request.maxSaleRatePercent?.div(100.0))
    appendCraftingRangePredicate(predicates, params, "c.sold_per_day", ">=", request.minSoldPerDay)
    appendCraftingRangePredicate(predicates, params, "c.sold_per_day", "<=", request.maxSoldPerDay)
    appendCraftingRangePredicate(predicates, params, "c.reagent_cost", ">=", request.minReagentCost)
    appendCraftingRangePredicate(predicates, params, "c.reagent_cost", "<=", request.maxReagentCost)
    appendCraftingRangePredicate(predicates, params, "c.output_unit_price", ">=", request.minOutputPrice)
    appendCraftingRangePredicate(predicates, params, "c.output_unit_price", "<=", request.maxOutputPrice)
    appendCraftingRangePredicate(predicates, params, "c.output_price_change_percent", ">=", request.minOutputPriceChangePercent)
    appendCraftingRangePredicate(predicates, params, "c.output_price_change_percent", "<=", request.maxOutputPriceChangePercent)
}

private fun appendCraftingRangePredicate(predicates: MutableList<String>, params: MutableList<Any?>, column: String, operator: String, value: Number?) {
    if (value == null) return
    predicates += "$column IS NOT NULL AND $column $operator ?"
    params += value
}

private fun String.escapeCraftingLike(): String = replace("!", "!!").replace("%", "!%").replace("_", "!_")
