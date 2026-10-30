package com.bornoutofcuriosity.radiogardentv

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Keeps the TV screen clean: blocks Google's ad and consent (Funding Choices) servers, and hides
 * what the page leaves behind (empty ad box, "Get our free Android app" banner, cookie notice).
 *
 * Station streams and artwork come from radio.garden's own hosts, which are never blocked.
 */
class AdFreeClient : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val host = request.url.host ?: return null
        if (BLOCKED_HOSTS.none { host == it || host.endsWith(".$it") }) return null
        return WebResourceResponse(
            "text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)),
        )
    }

    // The site is a single-page app, so the style survives in-site navigation; it is added again
    // whenever a new document loads.
    override fun onPageCommitVisible(view: WebView, url: String) = injectStyle(view)

    override fun onPageFinished(view: WebView, url: String) = injectStyle(view)

    private fun injectStyle(view: WebView) {
        view.evaluateJavascript(
            """
            (function () {
              if (document.getElementById('rgtv-clean')) return;
              var s = document.createElement('style');
              s.id = 'rgtv-clean';
              s.textContent = ${jsString(HIDE_CSS)};
              (document.head || document.documentElement).appendChild(s);
            })();
            """.trimIndent(),
            null,
        )
    }

    private fun jsString(s: String) = "\"" + s.replace("\"", "\\\"").replace("\n", " ") + "\""

    private companion object {
        val BLOCKED_HOSTS = listOf(
            "googlesyndication.com",
            "doubleclick.net",
            "googleadservices.com",
            "googletagservices.com",
            "adservice.google.com",
            "adtrafficquality.google",
            "fundingchoicesmessages.google.com",
        )

        // The site uses CSS modules (class "_banner_517ep_1"); match the stable name part only,
        // so a site update that changes the hash suffix doesn't bring the clutter back.
        val HIDE_CSS = """
            ins.adsbygoogle,
            [class*="_container_"]:has(> ins.adsbygoogle),
            [class*="_playTabBarContainer_"] > [class*="_banner_"],
            [class*="_privacyPolicyNotice_"],
            .fc-consent-root, .fc-dialog-container
            { display: none !important; }
        """.trimIndent()
    }
}
