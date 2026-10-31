package io.github.aasifashraf.unscroll.engine.rules

/** Website and keyword matching for text read from a browser's address bar. Pure. */
object UrlRules {
    private val hostPattern = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$")

    fun isBlocked(addressBar: String, sites: Set<String>, keywords: Set<String>): Boolean {
        val text = addressBar.trim().lowercase()
        if (text.isEmpty()) return false
        if (keywords.any { it.isNotBlank() && text.contains(it.lowercase()) && hostOf(text) != null }) return true
        val host = hostOf(text) ?: return false
        return sites.any { site -> host == site || host.endsWith(".$site") }
    }

    /** Host of a URL-ish string ("https://x.com/a", "x.com", "user@x.com:8080"), or null if it isn't one. */
    fun hostOf(text: String): String? {
        val trimmed = text.trim().lowercase()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return null
        val withoutScheme = trimmed.substringAfter("://", trimmed)
        val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':')
        return host.takeIf { hostPattern.matches(it) }
    }

    /** Cleans what the user typed into a site entry: "https://www.Reddit.com/r" → "reddit.com". */
    fun normalizeSite(input: String): String? {
        val host = hostOf(input) ?: return null
        return host.removePrefix("www.")
    }
}
