package io.github.aasifashraf.unscroll.engine.shortform

/** One app's short-video feed and the screen names only its full-screen player uses. */
data class ShortFormApp(
    val packageName: String,
    /** Shown in Unscroll, e.g. "Instagram Reels". */
    val feedName: String,
    /**
     * Resource names (without "package:id/") of views that exist only on the full-screen player,
     * never on the home feed, messages, profile or search. Unknown names simply never match.
     */
    val viewIds: List<String>,
    /** Endings of activity class names that are the player screen. */
    val activityHints: List<String> = emptyList(),
)

/**
 * The short-form detection map. Shipped in the app and changed only by app updates (Unscroll
 * has no network access). When an app renames its views the map stops matching: detection
 * fails open, and the fallback in [ShortFormRules] may take over.
 */
object ShortFormMap {
    /** Bump whenever the map changes, so "may be outdated" marks from the old map are cleared. */
    const val REVISION = 1

    val apps: List<ShortFormApp> = listOf(
        // Instagram calls Reels "clips" internally; the vertical pager of the Reels player.
        ShortFormApp("com.instagram.android", "Instagram Reels", listOf("clips_viewer_view_pager")),
        // The Shorts player (reel = Short). Checked to exist in YouTube 19.17.42's resources.
        ShortFormApp(
            "com.google.android.youtube",
            "YouTube Shorts",
            listOf("reel_watch_player", "reel_player_page_container", "reel_watch_fragment_root", "reel_recycler"),
        ),
        // Facebook builds its screens without view IDs; the Reels viewer has its own activity.
        ShortFormApp("com.facebook.katana", "Facebook Reels", emptyList(), activityHints = listOf("FbShortsViewerActivity")),
        // No reliable public IDs: detected by the fallback only.
        ShortFormApp("com.snapchat.android", "Snapchat Spotlight", emptyList()),
    )

    private val byPackage = apps.associateBy { it.packageName }

    val packages: Set<String> = apps.mapTo(LinkedHashSet()) { it.packageName }

    fun forPackage(pkg: String): ShortFormApp? = byPackage[pkg]

    /** View IDs in the form `findAccessibilityNodeInfosByViewId` expects. */
    fun fullViewIds(app: ShortFormApp): List<String> = app.viewIds.map { "${app.packageName}:id/$it" }

    /** Whether the window class [className] (from a window-state event) is [app]'s player screen. */
    fun matchesActivity(app: ShortFormApp, className: String?): Boolean =
        className != null && app.activityHints.any { className.endsWith(".$it") || className == it }
}
