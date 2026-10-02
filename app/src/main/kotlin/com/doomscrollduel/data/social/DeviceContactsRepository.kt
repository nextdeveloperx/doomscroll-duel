package com.doomscrollduel.data.social

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import com.doomscrollduel.domain.model.EmailHash
import com.doomscrollduel.domain.social.ContactMatch
import com.doomscrollduel.domain.social.ContactsRepository
import com.doomscrollduel.domain.social.ContactsResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Reads the email addresses in the phone's contacts, hashes each one on the phone, and looks up ONLY the hashes in the
 * `emailIndex` collection (one document per hash). Names, phone numbers and the addresses themselves never leave the phone,
 * and nothing is read before the person has allowed the Contacts permission. (The Cloud Function `matchContacts` does the
 * same job on the server once the project is on the Blaze plan; this works without it.)
 */
@Singleton
class DeviceContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ContactsRepository {

    override fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    override suspend fun findFriends(): ContactsResult {
        if (!hasPermission()) return ContactsResult.NoPermission
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return ContactsResult.NotSignedIn
        val hashes = withContext(Dispatchers.IO) { EmailHash.ofAll(readEmails(), LOOKUP_LIMIT) }
        if (hashes.isEmpty()) return ContactsResult.Found(emptyList())
        return try {
            val db = FirebaseFirestore.getInstance()
            val friendIds = db.collection("users").document(me).collection("friends").get().await().documents.map { it.id }.toSet()
            // One document per hash: the index can be read this way but never listed.
            val uids = coroutineScope {
                hashes.chunked(CHUNK).map { part ->
                    async { part.mapNotNull { hash -> db.collection("emailIndex").document(hash).get().await().getString("uid") } }
                }.awaitAll().flatten()
            }.filter { it != me }.distinct()
            val matches = coroutineScope {
                uids.chunked(CHUNK).map { part ->
                    async {
                        part.mapNotNull { uid ->
                            val entry = db.collection("directory").document(uid).get().await()
                            val username = entry.getString("username") ?: return@mapNotNull null
                            ContactMatch(uid, username, entry.getString("displayName") ?: username, isFriend = uid in friendIds)
                        }
                    }
                }.awaitAll().flatten()
            }
            ContactsResult.Found(matches.sortedBy { it.displayName.lowercase() })
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "contact lookup failed: ${e.code}", e)
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) ContactsResult.NoNetwork else ContactsResult.Failed
        } catch (e: IOException) {
            ContactsResult.NoNetwork
        }
    }

    private fun readEmails(): List<String> {
        val emails = ArrayList<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            null, null, null,
        )?.use { cursor ->
            val column = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (cursor.moveToNext() && emails.size < READ_LIMIT) {
                cursor.getString(column)?.let(emails::add)
            }
        }
        return emails
    }

    private companion object {
        const val TAG = "ContactsRepository"

        /** Enough for any real address book; keeps a huge one from taking long. */
        const val READ_LIMIT = 6_000

        /** Each hash is one read, so only this many contact addresses are looked up in one go. */
        const val LOOKUP_LIMIT = 400
        const val CHUNK = 25
    }
}
