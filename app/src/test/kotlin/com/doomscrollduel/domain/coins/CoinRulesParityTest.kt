package com.doomscrollduel.domain.coins

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server reads functions/src/coin-rules.json; the app reads CoinRules.kt. They must say the same thing. */
class CoinRulesParityTest {
    private fun rules(): JsonObject {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val f = File(dir, "functions/src/coin-rules.json")
            if (f.exists()) return Json.parseToJsonElement(f.readText()).jsonObject
            dir = dir.parentFile
        }
        error("functions/src/coin-rules.json not found above ${System.getProperty("user.dir")}")
    }

    @Test fun `numbers match`() {
        val j = rules()
        assertEquals(CoinRules.DAILY_EARN_CAP, j["dailyEarnCap"]!!.jsonPrimitive.int)
        assertEquals(CoinRules.WELCOME_COINS, j["welcomeCoins"]!!.jsonPrimitive.int)
        assertEquals(CoinRules.STAKE_OPTIONS, j["stakeOptions"]!!.jsonArray.map { it.jsonPrimitive.int })
        assertEquals(CoinRules.DUEL_WIN_MIN_DURATION_MS, j["duelWinMinDurationMs"]!!.jsonPrimitive.long)
        assertEquals(CoinRules.STREAK_DAY_MIN, j["streakDayMin"]!!.jsonPrimitive.int)
        assertEquals(CoinRules.STREAK_MILESTONES, j["streakMilestones"]!!.jsonObject.mapKeys { it.key.toInt() }.mapValues { it.value.jsonPrimitive.int })
    }

    @Test fun `earn sources match`() {
        val earn = rules()["earn"]!!.jsonObject
        assertEquals(EarnSource.entries.map { it.id }.toSet(), earn.keys)
        for (s in EarnSource.entries) {
            val e = earn[s.id]!!.jsonObject
            assertEquals(s.id, s.coins, e["coins"]!!.jsonPrimitive.int)
            assertEquals(s.id, s.perDayLimit, e["perDayLimit"]!!.jsonPrimitive.int)
            assertEquals(s.id, s.countsTowardDailyCap, e["countsTowardDailyCap"]!!.jsonPrimitive.boolean)
        }
    }

    @Test fun `catalog matches`() {
        val cat = rules()["catalog"]!!.jsonArray.map { it.jsonObject }
        val server = cat.map { listOf(it["id"]!!.jsonPrimitive.content, it["kind"]!!.jsonPrimitive.content, it["access"]!!.jsonPrimitive.content, it["price"]!!.jsonPrimitive.int.toString()) }
        val app = CoinRules.CATALOG.map { listOf(it.id, it.kind.name, it.access.name, it.price.toString()) }
        assertEquals(app, server)
        assertTrue(CoinRules.CATALOG.none { it.access == ItemAccess.PRO && it.price != 0 })
    }
}
