package com.doomscrollduel.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

sealed interface GoogleSignInOutcome {
    data class Token(val idToken: String) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome

    /** Google sign-in is not switched on in the Firebase console yet (or `google-services.json` is older than that). */
    data object NotConfigured : GoogleSignInOutcome

    /** The phone has no Google account to choose. */
    data object NoAccount : GoogleSignInOutcome
    data object Failed : GoogleSignInOutcome
}

/** Asks Android's account picker for a Google ID token, which Firebase then trades for a signed-in user. */
object GoogleSignInHelper {
    /**
     * The OAuth web client id. The Google Services plugin writes it into a string called `default_web_client_id` only
     * when Google sign-in is enabled in the Firebase console, so it is looked up by name and may legitimately be missing.
     */
    fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }

    suspend fun requestIdToken(activity: Activity): GoogleSignInOutcome {
        val clientId = webClientId(activity) ?: return GoogleSignInOutcome.NotConfigured
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleSignInOutcome.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleSignInOutcome.Failed
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInOutcome.Cancelled
        } catch (e: NoCredentialException) {
            GoogleSignInOutcome.NoAccount
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInOutcome.Failed
        } catch (e: GetCredentialException) {
            GoogleSignInOutcome.Failed
        }
    }
}
