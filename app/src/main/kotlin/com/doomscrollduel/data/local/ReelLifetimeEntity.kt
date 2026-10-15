package com.doomscrollduel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row only ([id] is always 0): every valid reel ever counted, across every app and every day, NEVER pruned.
 * This is the source of truth for reel-milestone coin rewards (see
 * [com.doomscrollduel.domain.coins.ReelMilestoneRules]) — deliberately a separate table from `reel_counts`, which
 * [ReelCountDao.deleteBefore] prunes after [com.doomscrollduel.data.repository.RoomReelRepository.RETENTION_DAYS]
 * days. Milestone progress must never regress just because old daily history aged out.
 */
@Entity(tableName = "reel_lifetime")
data class ReelLifetimeEntity(
    @PrimaryKey val id: Int = 0,
    val totalReels: Int,
)
