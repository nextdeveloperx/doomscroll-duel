package com.doomscrollduel.blocking

import com.doomscrollduel.domain.blocking.Buddy
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The friends one can pick as the unlock buddy. */
interface BuddyDirectory {
    val friends: Flow<List<Buddy>>
}

/**
 * Until friends exist (sign-in and friend adding are not built yet) there is nobody to pick, and the Settings
 * screen says so. This is the honest answer, not sample data.
 */
class EmptyBuddyDirectory @Inject constructor() : BuddyDirectory {
    override val friends: Flow<List<Buddy>> = flowOf(emptyList())
}
