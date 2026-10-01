package com.doomscrollduel.domain.billing

/**
 * A plan as Google Play prices it. The price text and amount come from Play, in the user's currency, never from us.
 */
data class PlanOffer(
    val plan: ProPlan,
    val formattedPrice: String,
    val priceMicros: Long,
    val currencyCode: String,
    val offerToken: String,
)

object PlanPricing {
    /**
     * How much cheaper the yearly plan is than twelve months of the monthly one, as a whole percent, or null when it
     * cannot be said honestly (different currencies, missing plan, or yearly is not cheaper).
     */
    fun yearlySavingPercent(offers: List<PlanOffer>): Int? {
        val monthly = offers.firstOrNull { it.plan == ProPlan.MONTHLY } ?: return null
        val yearly = offers.firstOrNull { it.plan == ProPlan.YEARLY } ?: return null
        if (monthly.currencyCode != yearly.currencyCode || monthly.priceMicros <= 0L) return null
        val twelve = monthly.priceMicros * 12
        if (yearly.priceMicros >= twelve) return null
        return (((twelve - yearly.priceMicros) * 100) / twelve).toInt().takeIf { it >= 1 }
    }

    /** Yearly price divided by 12, written in the plan's own currency, for the "that is x a month" line. */
    fun yearlyPerMonthText(offers: List<PlanOffer>, locale: java.util.Locale): String? {
        val yearly = offers.firstOrNull { it.plan == ProPlan.YEARLY } ?: return null
        val currency = runCatching { java.util.Currency.getInstance(yearly.currencyCode) }.getOrNull() ?: return null
        val format = java.text.NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }
        return format.format(yearly.priceMicros / 12 / 1_000_000.0)
    }
}

/** Play product and base plan ids. Created in Play Console; must match `functions/src/playMapping.ts`. */
object ProProduct {
    const val PRODUCT_ID = "doomscroll_pro"
    const val BASE_PLAN_MONTHLY = "monthly"
    const val BASE_PLAN_YEARLY = "yearly"

    fun planOf(basePlanId: String?): ProPlan? = when (basePlanId) {
        BASE_PLAN_MONTHLY -> ProPlan.MONTHLY
        BASE_PLAN_YEARLY -> ProPlan.YEARLY
        else -> null
    }
}
