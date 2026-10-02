package com.doomscrollduel.data.social

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import kotlinx.coroutines.tasks.await

/** The answer of a Cloud Function call, with the ways it can go wrong already sorted into the few the app cares about. */
internal sealed interface CallResult {
    /** [data] is whatever the function returned, as a map. */
    data class Ok(val data: Map<*, *>) : CallResult
    data object NotSignedIn : CallResult
    data object NoNetwork : CallResult
    data object Failed : CallResult
}

/** One place that talks to the callable functions in `functions/src/social.ts`. */
internal suspend fun callFunction(name: String, args: Map<String, Any?> = emptyMap()): CallResult {
    if (FirebaseAuth.getInstance().currentUser == null) return CallResult.NotSignedIn
    return try {
        val data = FirebaseFunctions.getInstance().getHttpsCallable(name).call(args).await().data as? Map<*, *>
        if (data == null) CallResult.Failed else CallResult.Ok(data)
    } catch (e: FirebaseFunctionsException) {
        when (e.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> CallResult.NotSignedIn
            FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> CallResult.NoNetwork
            else -> CallResult.Failed
        }
    } catch (e: IOException) {
        CallResult.NoNetwork
    } catch (e: Exception) {
        // Includes the "function does not exist yet" case before the backend is deployed.
        CallResult.Failed
    }
}

internal fun Map<*, *>.string(key: String): String? = this[key] as? String
