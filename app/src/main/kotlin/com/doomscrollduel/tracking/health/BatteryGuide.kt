package com.doomscrollduel.tracking.health

/** Phone makers whose battery managers kill background apps beyond what stock Android does. */
enum class OemFamily {
    XIAOMI,
    REALME_OPPO,
    VIVO,
    SAMSUNG,
    OTHER;

    companion object {
        /** [manufacturer] is `Build.MANUFACTURER`, [brand] is `Build.BRAND` (Redmi and Poco report Xiaomi's brand). */
        fun from(manufacturer: String?, brand: String?): OemFamily {
            val names = listOfNotNull(manufacturer, brand).map { it.trim().lowercase() }
            fun has(vararg keys: String) = names.any { name -> keys.any { name == it } }
            return when {
                has("xiaomi", "redmi", "poco") -> XIAOMI
                has("realme", "oppo", "oneplus") -> REALME_OPPO
                has("vivo", "iqoo") -> VIVO
                has("samsung") -> SAMSUNG
                else -> OTHER
            }
        }
    }
}

/** One instruction in the battery guide. The screen maps each to its own translated sentence. */
enum class BatteryStep {
    OPEN_BATTERY_LIST,
    PICK_APP_UNRESTRICTED,
    XIAOMI_AUTOSTART,
    XIAOMI_BATTERY_SAVER,
    REALME_AUTO_LAUNCH,
    REALME_BACKGROUND_ACTIVITY,
    VIVO_AUTOSTART,
    VIVO_BACKGROUND_POWER,
    SAMSUNG_UNRESTRICTED,
    SAMSUNG_SLEEPING_APPS,
    SAMSUNG_UNUSED_APPS,
    LOCK_IN_RECENTS,
}

object BatteryGuide {
    /** Steps for any phone, then the extras for [oem]. */
    fun stepsFor(oem: OemFamily): List<BatteryStep> {
        val common = listOf(BatteryStep.OPEN_BATTERY_LIST, BatteryStep.PICK_APP_UNRESTRICTED)
        val extra = when (oem) {
            OemFamily.XIAOMI -> listOf(
                BatteryStep.XIAOMI_AUTOSTART,
                BatteryStep.XIAOMI_BATTERY_SAVER,
                BatteryStep.LOCK_IN_RECENTS,
            )
            OemFamily.REALME_OPPO -> listOf(
                BatteryStep.REALME_AUTO_LAUNCH,
                BatteryStep.REALME_BACKGROUND_ACTIVITY,
                BatteryStep.LOCK_IN_RECENTS,
            )
            OemFamily.VIVO -> listOf(
                BatteryStep.VIVO_AUTOSTART,
                BatteryStep.VIVO_BACKGROUND_POWER,
                BatteryStep.LOCK_IN_RECENTS,
            )
            OemFamily.SAMSUNG -> listOf(
                BatteryStep.SAMSUNG_UNRESTRICTED,
                BatteryStep.SAMSUNG_SLEEPING_APPS,
                BatteryStep.SAMSUNG_UNUSED_APPS,
            )
            OemFamily.OTHER -> emptyList()
        }
        return common + extra
    }
}
