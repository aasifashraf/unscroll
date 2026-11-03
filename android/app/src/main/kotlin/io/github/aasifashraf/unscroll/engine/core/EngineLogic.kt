package io.github.aasifashraf.unscroll.engine.core

import android.media.AudioManager
import io.github.aasifashraf.unscroll.engine.clock.DayState
import io.github.aasifashraf.unscroll.engine.rules.BlockReason
import io.github.aasifashraf.unscroll.engine.rules.EngineState
import io.github.aasifashraf.unscroll.engine.rules.RuleEngine
import io.github.aasifashraf.unscroll.engine.store.PersistedState
import kotlin.math.ceil

/** Pure helpers behind [Engine], kept separate so they can be unit-tested. */
object EngineLogic {
    /**
     * Whether a window from [pkg] means the user switched apps. Only apps with a launcher icon,
     * launchers ([trackable]) and call/emergency screens ([urgent]) count. The notification shade,
     * keyboards, volume panel and system dialogs don't: they must not dismiss a block screen or
     * cancel a limit timer. Unscroll itself is handled separately.
     */
    fun isAppSwitch(pkg: String, ownPackage: String, trackable: Set<String>, urgent: Set<String>): Boolean =
        pkg != ownPackage && (pkg in trackable || pkg in urgent)

    /**
     * Extra minutes for today after one "5 more minutes" tap: always [EXTENSION]-minutes from now,
     * even if the block screen stayed up a while (the app keeps counting underneath it).
     */
    fun extraMinutesAfterExtension(limitMinutes: Int, extraNow: Int, usedMillis: Long): Int {
        val overByMinutes = ceil((usedMillis - limitMinutes * 60_000L) / 60_000.0).toInt()
        return maxOf(extraNow + EXTENSION, overByMinutes + EXTENSION)
    }

    const val EXTENSION = 5

    /**
     * What one "5 more minutes" tap extends: the [key] in extraMinutesToday/extensionsUsedToday,
     * the daily limit it adds to, and the usage that counts against it.
     */
    data class Extension(val key: String, val limitMinutes: Int, val usedMillis: Long)

    /**
     * The limit a block screen for [pkg] can extend, or null when the block can't be extended
     * (not a time limit, or the rule or group was removed or changed meanwhile).
     * [appUsedMillis] is [pkg]'s usage today; [groupUsageMillis] the summed usage per group id.
     */
    fun extensionFor(
        reason: BlockReason,
        groupId: String?,
        pkg: String,
        state: EngineState,
        appUsedMillis: Long,
        groupUsageMillis: Map<String, Long>,
    ): Extension? = when (reason) {
        BlockReason.DAILY_LIMIT -> state.rules[pkg]?.takeUnless { it.blocked }?.dailyLimitMinutes
            ?.let { Extension(pkg, it, appUsedMillis) }
        BlockReason.GROUP_LIMIT -> state.groups.firstOrNull { it.id == groupId }
            ?.let { Extension(RuleEngine.groupKey(it.id), it.dailyLimitMinutes, groupUsageMillis[it.id] ?: 0L) }
        else -> null
    }

    /** The first [max] characters (code points) of [text], never splitting an emoji in two. */
    fun truncateCodePoints(text: String, max: Int): String {
        if (text.codePointCount(0, text.length) <= max) return text
        return text.substring(0, text.offsetByCodePoints(0, max))
    }

    /** One app's session (elapsedRealtime): when it began, and when the app last left the front. */
    data class AppSession(val startedAt: Long, val leftAt: Long? = null)

    /** Coming back to an app within this time continues its session (screen off/on, a quick trip home). */
    const val SESSION_GRACE_MILLIS = 60_000L

    /**
     * Sessions after [front] came to the front at [now] (null = no tracked app: screen off,
     * Unscroll). [front] keeps its session if it is still in front or came back within
     * [SESSION_GRACE_MILLIS]; otherwise a new one starts. Other apps are marked as left, and
     * forgotten once the grace has passed.
     */
    fun sessionsAfterSwitch(sessions: Map<String, AppSession>, front: String?, now: Long): Map<String, AppSession> {
        fun fresh(s: AppSession) = s.leftAt == null || now - s.leftAt <= SESSION_GRACE_MILLIS
        val others = sessions.filterKeys { it != front }
            .mapValues { (_, s) -> if (s.leftAt == null) s.copy(leftAt = now) else s }
            .filterValues(::fresh)
        if (front == null) return others
        val kept = sessions[front]?.takeIf(::fresh)?.copy(leftAt = null)
        return others + (front to (kept ?: AppSession(now)))
    }

    /**
     * Saved cool-downs (trusted time) as boot-clock ends, for a fresh process or after a reboot.
     * Ended ones are dropped; none lasts longer than one cool-down from now.
     */
    fun inBootCooldowns(persisted: Map<String, Long>, trustedNow: Long, elapsedNow: Long): Map<String, Long> =
        persisted.filterValues { it > trustedNow }
            .mapValues { (_, until) -> elapsedNow + minOf(until - trustedNow, RuleEngine.SESSION_COOLDOWN_MILLIS) }

    /**
     * Whether switching from [previous] to [next] answers an unanswered pause for [pausing] with
     * "I don't need it": leaving it for home or another app does; a call ([urgent]) or a
     * re-check of the same app doesn't.
     */
    fun countsAsDecline(pausing: String?, previous: String?, next: String, urgent: Set<String>): Boolean =
        pausing != null && pausing == previous && next != previous && next !in urgent

    /** The phone is ringing or in a (phone or VoIP) call, from AudioManager.getMode(). */
    fun isCallMode(audioMode: Int): Boolean = audioMode == AudioManager.MODE_RINGTONE ||
        audioMode == AudioManager.MODE_IN_CALL || audioMode == AudioManager.MODE_IN_COMMUNICATION

    /** A trusted time as a time on the phone's clock (what the user sees in the status bar). */
    fun toWallClock(trustedMillis: Long, trustedNow: Long, wallNow: Long): Long = trustedMillis + (wallNow - trustedNow)

    /** [cooldowns] with [pkg] closed until [until]; ones already over at [now] are dropped (any one clock frame). */
    fun withCooldown(cooldowns: Map<String, Long>, pkg: String, until: Long, now: Long): Map<String, Long> =
        cooldowns.filterValues { it > now } + (pkg to until)

    /** The user chose not to open an app on the pause screen. */
    fun withDecline(state: PersistedState): PersistedState =
        state.copy(declinedToday = state.declinedToday + 1, declinedTotal = state.declinedTotal + 1)

    /** How often a repeated short-form detection may rewrite the saved "last detected" time. */
    const val SHORT_FORM_SAVE_EVERY_MILLIS = 60_000L

    /**
     * [state] for detection map [revision]. A new map (after an Unscroll update) may work again,
     * so the old "may be outdated" marks and matched versions are dropped. The user's choices stay.
     */
    fun withShortFormRevision(state: PersistedState, revision: Int): PersistedState =
        if (state.shortFormMapRevision == revision) {
            state
        } else {
            state.copy(shortFormSuspect = emptyMap(), shortFormMatchedVersion = emptyMap(), shortFormMapRevision = revision)
        }

    /**
     * A short-form feed in [pkg] was detected and closed at trusted time [trustedNow]. A map match
     * records the app version the map works in; a fallback match marks that version as maybe
     * outdated. The time alone is saved at most once a minute (unchanged state = same instance).
     */
    fun withShortFormDetection(
        state: PersistedState,
        pkg: String,
        trustedNow: Long,
        appVersion: Long?,
        byFallback: Boolean,
    ): PersistedState {
        var next = state
        val last = state.shortFormLastSeen[pkg]
        if (last == null || trustedNow - last >= SHORT_FORM_SAVE_EVERY_MILLIS || trustedNow < last) {
            next = next.copy(shortFormLastSeen = next.shortFormLastSeen + (pkg to trustedNow))
        }
        if (appVersion != null && !byFallback && state.shortFormMatchedVersion[pkg] != appVersion) {
            next = next.copy(shortFormMatchedVersion = next.shortFormMatchedVersion + (pkg to appVersion))
        }
        if (appVersion != null && byFallback) next = withShortFormSuspect(next, pkg, appVersion)
        return next
    }

    /** Detection in [pkg] looked wrong in [appVersion] (unknown version: nothing to record). */
    fun withShortFormSuspect(state: PersistedState, pkg: String, appVersion: Long?): PersistedState =
        if (appVersion == null || state.shortFormSuspect[pkg] == appVersion) {
            state
        } else {
            state.copy(shortFormSuspect = state.shortFormSuspect + (pkg to appVersion))
        }

    /** The user turned [pkg]'s feed block on or off; a fresh start also clears its outdated mark. */
    fun withShortFormBlocked(state: PersistedState, pkg: String, blocked: Boolean): PersistedState {
        val set = if (blocked) state.engine.shortFormBlocked + pkg else state.engine.shortFormBlocked - pkg
        return state.copy(engine = state.engine.copy(shortFormBlocked = set), shortFormSuspect = state.shortFormSuspect - pkg)
    }

    /**
     * [state] after the day clock moved to [next]. A new day resets the per-day extras and
     * counters. Otherwise the clock is saved only now and then (after a minute or a reboot), not
     * on every event; an unchanged state is returned as the same instance.
     */
    fun withDay(state: PersistedState, next: DayState): PersistedState {
        val previous = state.day
        return when {
            previous == null || next.day != previous.day -> state.copy(
                day = next,
                engine = state.engine.copy(extraMinutesToday = emptyMap()),
                extensionsUsedToday = emptyMap(),
                declinedToday = 0,
            )
            next.trustedWallMillis - previous.trustedWallMillis > 60_000 || next.bootCount != previous.bootCount ->
                state.copy(day = next)
            else -> state
        }
    }
}
