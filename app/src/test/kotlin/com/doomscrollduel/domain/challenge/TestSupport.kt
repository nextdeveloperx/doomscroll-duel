package com.doomscrollduel.domain.challenge

import java.time.Instant

val T0: Instant = Instant.parse("2026-10-01T10:00:00Z")

fun minutes(m: Long): java.time.Duration = java.time.Duration.ofMinutes(m)

fun hours(h: Long): java.time.Duration = java.time.Duration.ofHours(h)

val ROHAN = PlayerId("rohan")
val AMAN = PlayerId("aman")
val RIYA = PlayerId("riya")
