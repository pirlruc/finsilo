package com.pirlruc.finsilo.domain.entitlement

/**
 * Features that sit behind the Pro entitlement gate.
 * XIRR, the IRS annual report, and the dividend calendar stay free and are not listed here.
 */
enum class PremiumFeature {
    MULTI_PORTFOLIO,
    REBALANCE,
    FIRE_DRIP,
}

/** Whether a Pro feature may be shown. The app supplies the flag; this module does not bill. */
fun interface Entitlements {
    /** True when [feature] is unlocked for this build. */
    fun allows(feature: PremiumFeature): Boolean
}

/** Every gated feature follows one boolean. There is no per-feature secret. */
class FlagEntitlements(private val unlocked: Boolean) : Entitlements {
    override fun allows(feature: PremiumFeature): Boolean = unlocked
}

/** Gradle `premiumUnlocked` parser. Absent or anything other than false stays unlocked. */
object PremiumFlag {
    /** Maps the Gradle property. Only an explicit false locks the gate. */
    fun parse(raw: String?): Boolean = !raw.equals("false", ignoreCase = true)
}
