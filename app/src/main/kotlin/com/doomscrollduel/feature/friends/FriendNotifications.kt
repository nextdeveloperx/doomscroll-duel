package com.doomscrollduel.feature.friends

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.doomscrollduel.MainActivity
import com.doomscrollduel.R

/**
 * The two notifications about friends. They carry only a display name and an invite code, nothing about reels.
 *
 *  - `friend_invite`: someone invited you. Tapping opens the accept screen with the code ("Join karo").
 *  - `friend_joined`: someone accepted your invite or added you by username.
 */
object FriendNotifications {
    const val CHANNEL_ID = "friends"

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_friends_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notif_friends_channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showInvite(context: Context, code: String, fromName: String) {
        val open = PendingIntent.getActivity(
            context,
            code.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("doomscrollduel://invite/$code"), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        show(
            context,
            id = code.hashCode(),
            title = context.getString(R.string.notif_invite_title, fromName),
            body = context.getString(R.string.notif_invite_body),
            open = open,
        )
    }

    /** An invite left in my inbox by someone from the People list. Tapping opens the Dost page on the invites tab. */
    fun showInviteFromPerson(context: Context, fromUid: String, fromName: String) {
        show(
            context,
            id = ("invite:$fromUid").hashCode(),
            title = context.getString(R.string.notif_invite_title, fromName),
            body = context.getString(R.string.notif_invite_inbox_body),
            open = openFriends(context, fromUid.hashCode()),
        )
    }

    /** Somebody challenged me to a battle. Tapping opens the Dost page, where the challenge waits under Invites. */
    fun showChallenge(context: Context, duelId: String, fromName: String) {
        show(
            context,
            id = ("challenge:$duelId").hashCode(),
            title = context.getString(R.string.notif_challenge_title, fromName),
            body = context.getString(R.string.notif_challenge_body),
            open = openFriends(context, duelId.hashCode()),
        )
    }

    /** The other player accepted my challenge; the battle has begun. Tapping opens it. */
    fun showDuelStarted(context: Context, duelId: String, opponentName: String) {
        val open = PendingIntent.getActivity(
            context,
            ("started:$duelId").hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("doomscrollduel://duel/$duelId"), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        show(
            context,
            id = ("started:$duelId").hashCode(),
            title = context.getString(R.string.notif_duel_started_title, opponentName),
            body = context.getString(R.string.notif_duel_started_body),
            open = open,
        )
    }

    /** Somebody asked me into their broadcast. Tapping opens the Broadcast page and joins that room. */
    fun showBroadcastInvite(context: Context, roomId: String, fromName: String, title: String) {
        val open = PendingIntent.getActivity(
            context,
            ("bc:$roomId").hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("doomscrollduel://broadcast/join/$roomId"), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        show(
            context,
            id = ("bc:$roomId").hashCode(),
            title = context.getString(R.string.notif_bc_title, fromName),
            body = context.getString(R.string.notif_bc_body, title),
            open = open,
        )
    }

    /** The battle is over. Tapping opens its result. */
    fun showDuelEnded(context: Context, duelId: String, opponentName: String) {
        val open = PendingIntent.getActivity(
            context,
            ("ended:$duelId").hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("doomscrollduel://result/$duelId"), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        show(
            context,
            id = ("ended:$duelId").hashCode(),
            title = context.getString(R.string.notif_duel_ended_title, opponentName),
            body = context.getString(R.string.notif_duel_ended_body),
            open = open,
        )
    }

    private fun openFriends(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(Intent.ACTION_VIEW, Uri.parse("doomscrollduel://friends"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun showJoined(context: Context, fromName: String) {
        val open = openFriends(context, 1)
        show(
            context,
            id = ("joined:$fromName").hashCode(),
            title = context.getString(R.string.notif_joined_title, fromName),
            body = context.getString(R.string.notif_joined_body),
            open = open,
        )
    }

    private fun show(context: Context, id: Int, title: String, body: String, open: PendingIntent) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_brain)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_SOCIAL)
            .build()
        manager.notify(id, notification)
    }
}
