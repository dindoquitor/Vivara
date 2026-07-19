package com.vivara.browser.utils

import android.net.Uri

/**
 * Lightweight redirect chain detector.
 * Tracks rapid successive URL changes and blocks suspicious redirect chains.
 *
 * A redirect chain is considered abusive when:
 * - 3+ redirects occur within [WINDOW_MS] (2 seconds)
 * - At least one redirect is not user-initiated
 *
 * Same-domain redirects (example.com → www.example.com) are not counted.
 * OAuth flows, payment gateways, and other legitimate multi-hop redirects
 * typically don't complete 3+ hops in under 2 seconds.
 */
class RedirectBlocker {

    companion object {
        private const val WINDOW_MS = 2000L       // time window for redirect chain detection
        private const val MAX_REDIRECTS = 3        // trigger threshold
        private const val MAX_HISTORY = 10          // max entries to track per instance
    }

    data class RedirectEntry(
        val url: String,
        val timestamp: Long,
        val isRedirect: Boolean
    )

    private val history = ArrayDeque<RedirectEntry>()

    /**
     * Record a navigation event.
     * @param url The URL being navigated to
     * @param isRedirect Whether this navigation was triggered by a redirect (server-side or JS)
     */
    fun record(url: String, isRedirect: Boolean) {
        history.addLast(RedirectEntry(url, System.currentTimeMillis(), isRedirect))
        if (history.size > MAX_HISTORY) {
            history.removeFirst()
        }
    }

    /**
     * Check whether the current navigation should be blocked as part of an abusive redirect chain.
     * Call BEFORE allowing the navigation to proceed.
     *
     * @param url The URL about to be loaded
     * @param isRedirect Whether this is a server/JS redirect (true) or user-initiated (false)
     * @param currentUrl The current page URL (null if no page loaded yet)
     * @return true if this redirect should be blocked
     */
    fun shouldBlock(url: String, isRedirect: Boolean, currentUrl: String?): Boolean {
        if (!isRedirect) {
            // User-initiated navigations don't count toward the chain
            return false
        }

        val now = System.currentTimeMillis()

        // Don't count same-domain redirects
        if (currentUrl != null && isSameDomain(currentUrl, url)) {
            return false
        }

        // Count redirects within the time window
        val recentRedirects = history.filter {
            (now - it.timestamp) <= WINDOW_MS && it.isRedirect
        }.size

        // +1 for the current redirect that's about to happen
        return (recentRedirects + 1) >= MAX_REDIRECTS
    }

    /** Clear history (e.g. when user manually navigates to a new page). */
    fun reset() {
        history.clear()
    }

    private fun isSameDomain(url1: String, url2: String): Boolean {
        return try {
            val host1 = Uri.parse(url1).host
            val host2 = Uri.parse(url2).host
            if (host1 == null || host2 == null) return false
            // Strip "www." prefix for comparison
            host1.removePrefix("www.") == host2.removePrefix("www.")
        } catch (_: Exception) {
            false
        }
    }
}
