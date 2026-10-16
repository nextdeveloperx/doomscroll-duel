package com.doomscrollduel.blocking

import com.doomscrollduel.domain.blocking.Buddy
import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.repository.FriendsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The friends one can pick as the unlock buddy. */
interface BuddyDirectory {
    val friends: Flow<List<Buddy>>
}

/** The person's real friends (the same list the Dost screen shows). Empty while signed out, which Settings says honestly. */
class FriendsBuddyDirectory @Inject constructor(friendsRepository: FriendsRepository) : BuddyDirectory {
    override val friends: Flow<List<Buddy>> = friendsRepository.friends.map { list ->
        list.filter { it.uid.isNotBlank() }.map { Buddy(PlayerId(it.uid), it.displayName.ifBlank { it.username }) }
    }
}
