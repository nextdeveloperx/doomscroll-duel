package com.doomscrollduel.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only way to record a handled (non-fatal) problem. It sends the exception's CLASS NAME and stack trace, never its
 * message, because messages can contain a username, a path or a server answer. [tag] must be a fixed word such as
 * "purchase_verify" chosen in code, never text from the user. Records nothing unless the person allowed crash reports
 * (Crashlytics collection is off otherwise).
 */
@Singleton
class CrashReporter @Inject constructor() {
    fun nonFatal(tag: String, error: Throwable) {
        val stripped = StrippedException("$tag:${error.javaClass.simpleName}")
        stripped.stackTrace = error.stackTrace
        FirebaseCrashlytics.getInstance().recordException(stripped)
    }

    /** Carries only the tag and the original class name. The original message and cause are left out on purpose. */
    class StrippedException(label: String) : RuntimeException(label)
}
