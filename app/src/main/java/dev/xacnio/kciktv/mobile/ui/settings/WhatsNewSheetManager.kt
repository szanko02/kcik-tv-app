/**
 * File: WhatsNewSheetManager.kt
 *
 * Description: Manages business logic, state, and UI interactions for Whats New Sheet.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.mobile.ui.settings

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dev.xacnio.kciktv.BuildConfig
import dev.xacnio.kciktv.R
import dev.xacnio.kciktv.mobile.MobilePlayerActivity
import dev.xacnio.kciktv.shared.data.model.GithubRelease
import dev.xacnio.kciktv.shared.data.repository.UpdateRepository
import dev.xacnio.kciktv.shared.data.repository.UpdateSource
import kotlinx.coroutines.launch

class WhatsNewSheetManager(
    private val activity: MobilePlayerActivity,
    private val repository: UpdateRepository
) {
    private companion object {
        // Assumed last seen when nothing usable is stored (older builds didn't track it reliably)
        const val DEFAULT_LAST_SEEN_VERSION = "2.4.4-beta-66f66fc"
    }

    private var activeDialog: Dialog? = null

    fun show(isAutoPopup: Boolean = false) {
        if (activeDialog?.isShowing == true) return

        if (isAutoPopup) {
            val source = UpdateSource.fromKey(activity.prefs.updateSource)
            activity.lifecycleScope.launch {
                val result = repository.getAllReleases(source)
                if (activity.isFinishing || activity.isDestroyed) return@launch
                if (result.isSuccess) {
                    val currentVer = BuildConfig.VERSION_NAME
                    val unseen = unseenReleases(result.getOrNull().orEmpty(), currentVer)
                        ?: return@launch // No release for this build

                    if (unseen.isEmpty()) {
                        // Nothing worth showing; mark as seen silently
                        activity.prefs.lastSeenWhatsNewVersion = currentVer
                        return@launch
                    }

                    createAndShowDialog(unseen, true)
                }
            }
        } else {
            // Manual open: show immediately and fetch
            createAndShowDialog(null, false)
        }
    }

    private fun matchesVersion(release: GithubRelease, version: String): Boolean {
        val tag = release.safeTagName
        return version.isNotBlank() &&
            (tag.equals("v$version", ignoreCase = true) || tag.equals(version, ignoreCase = true))
    }

    /** False for empty notes or GitHub's auto-generated "Full Changelog" link. */
    private fun hasNotes(release: GithubRelease): Boolean {
        val body = release.safeBody.trim()
        return body.isNotEmpty() && !body.startsWith("**Full Changelog**", ignoreCase = true)
    }

    /**
     * Releases from the current version back to (not including) the last seen one, newest
     * first, so updates that skip versions show every change. Null if the current build has
     * no release.
     */
    private fun unseenReleases(releases: List<GithubRelease>, currentVer: String): List<GithubRelease>? {
        val sorted = releases
            .filter { it.safeTagName.isNotBlank() }
            .sortedByDescending { it.safePublishedAt }
        val currentIdx = sorted.indexOfFirst { matchesVersion(it, currentVer) }
        if (currentIdx == -1) return null

        fun olderIndexOf(version: String): Int? =
            sorted.indexOfFirst { matchesVersion(it, version) }.takeIf { it > currentIdx }

        val lastSeenIdx = olderIndexOf(activity.prefs.lastSeenWhatsNewVersion)
            ?: olderIndexOf(DEFAULT_LAST_SEEN_VERSION)
            ?: (currentIdx + 1)

        return sorted.subList(currentIdx, lastSeenIdx).filter { hasNotes(it) }
    }

    private fun createAndShowDialog(preloadedReleases: List<GithubRelease>?, isAutoPopup: Boolean) {
        val view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_webview, null)

        if (isAutoPopup) {
            // Center Dialog Mode
            val dialog = Dialog(activity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(view)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val width = (activity.resources.displayMetrics.widthPixels * 0.9).toInt()
            val height = (activity.resources.displayMetrics.heightPixels * 0.7).toInt()
            dialog.window?.setLayout(width, height)

            val bg = GradientDrawable()
            bg.setColor(Color.parseColor("#121212"))
            bg.cornerRadius = 16f * activity.resources.displayMetrics.density
            view.background = bg

            activeDialog = dialog
        } else {
            // Bottom Sheet Mode
            val bsDialog = BottomSheetDialog(activity, R.style.BottomSheetDialogTheme)
            bsDialog.setContentView(view)

            val parentLayout = bsDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? FrameLayout
            parentLayout?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            activeDialog = bsDialog
        }

        val webView = view.findViewById<WebView>(R.id.webView)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val closeBtn = view.findViewById<View>(R.id.closeButton)

        closeBtn.setOnClickListener { activeDialog?.dismiss() }

        if (isAutoPopup) {
            activeDialog?.setOnDismissListener {
                activity.prefs.lastSeenWhatsNewVersion = BuildConfig.VERSION_NAME
            }
        }

        setupWebView(webView, isAutoPopup)

        if (preloadedReleases != null) {
            progressBar.visibility = View.GONE
            val html = generateHtml(preloadedReleases)
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        } else {
            progressBar.visibility = View.VISIBLE
            val source = UpdateSource.fromKey(activity.prefs.updateSource)
            activity.lifecycleScope.launch {
                val result = repository.getAllReleases(source)
                if (activity.isFinishing || activity.isDestroyed) return@launch
                if (result.isSuccess) {
                    var releases = result.getOrNull().orEmpty()
                        .filter { it.safeTagName.isNotBlank() }
                        .sortedByDescending { it.safePublishedAt }

                    val currentVer = BuildConfig.VERSION_NAME
                    val startIndex = releases.indexOfFirst { matchesVersion(it, currentVer) }
                    if (startIndex != -1) {
                        releases = releases.drop(startIndex)
                    }

                    val html = if (releases.isEmpty()) {
                        generateEmptyHtml(activity.getString(R.string.whats_new_empty))
                    } else {
                        generateHtml(releases)
                    }
                    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                } else {
                    val errorHtml = generateEmptyHtml(activity.getString(R.string.update_check_failed))
                    webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
                }
                progressBar.visibility = View.GONE
            }
        }

        activeDialog?.show()
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView(webView: WebView, isAutoPopup: Boolean) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.setBackgroundColor(0) // Transparent

        if (!isAutoPopup) {
            webView.setOnTouchListener { v, event ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                if ((event.action and android.view.MotionEvent.ACTION_MASK) == android.view.MotionEvent.ACTION_UP) {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                request.url?.let { uri ->
                    dev.xacnio.kciktv.mobile.util.DialogUtils.showLinkConfirmationDialog(activity, uri.toString())
                }
                return true
            }
        }
    }

    private fun generateEmptyHtml(message: String): String {
        val escaped = android.text.TextUtils.htmlEncode(message)
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        background-color: transparent;
                        color: #b0b0b0;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        margin: 0;
                        padding: 24px;
                        min-height: 60vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        text-align: center;
                    }
                    .empty-card {
                        background: #1a1a1a;
                        border: 1px solid #2c2c2e;
                        border-radius: 12px;
                        padding: 20px 24px;
                        font-size: 15px;
                        line-height: 1.5;
                    }
                </style>
            </head>
            <body>
                <div class="empty-card">$escaped</div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun generateHtml(releases: List<GithubRelease>): String {
        // Escape `</` so `</script>` inside release notes can never break out of the script tag.
        val jsonData = com.google.gson.Gson().toJson(releases).replace("</", "<\\/")

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { background-color: transparent; color: #e0e0e0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; padding: 16px; margin: 0; padding-bottom: 50px; }
                    h1, h2 { color: #53fc18; margin-top: 24px; margin-bottom: 4px; font-size: 20px; }
                    h3, h4 { color: #ffffff; margin-top: 16px; margin-bottom: 8px; font-size: 16px; }
                    .date { color: #888; font-size: 13px; margin-bottom: 16px; display: block; }
                    a { color: #53fc18; text-decoration: none; }
                    hr { border: 0; border-top: 1px solid #333; margin: 24px 0; }
                    ul, ol { padding-left: 20px; margin-top: 6px; margin-bottom: 12px; }
                    li { margin-bottom: 6px; line-height: 1.5; }
                    p { line-height: 1.5; margin: 8px 0; }
                    code { background: #222; padding: 2px 5px; border-radius: 4px; font-family: monospace; font-size: 13px; }
                    pre { background: #1a1a1a; padding: 12px; border-radius: 8px; overflow-x: auto; border: 1px solid #333; }
                    pre code { background: transparent; padding: 0; }
                    blockquote { border-left: 3px solid #53fc18; margin: 8px 0; padding-left: 12px; color: #aaa; }
                    img { max-width: 100%; border-radius: 8px; }
                    .badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 11px; font-weight: bold; margin-left: 8px; vertical-align: middle; }
                    .badge-pre { background: #ff9800; color: #000; }
                    .badge-stable { background: #53fc18; color: #000; }
                </style>
            </head>
            <body>
                <div id="content"></div>

                <script>
                    function escapeHtml(str) {
                        return String(str || "")
                            .replace(/&/g, "&amp;")
                            .replace(/</g, "&lt;")
                            .replace(/>/g, "&gt;")
                            .replace(/"/g, "&quot;");
                    }

                    function formatInline(text) {
                        const codeTokens = [];
                        let s = escapeHtml(text).replace(/`([^`]+)`/g, function(_, code) {
                            const idx = codeTokens.push("<code>" + code + "</code>") - 1;
                            return "\u0000CODE" + idx + "\u0000";
                        });
                        s = s.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2">$1</a>');
                        s = s.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
                        s = s.replace(/(^|[^*])\*([^*]+)\*/g, "$1<em>$2</em>");
                        s = s.replace(/\u0000CODE(\d+)\u0000/g, function(_, idx) {
                            return codeTokens[Number(idx)] || "";
                        });
                        return s;
                    }

                    function renderMarkdown(md) {
                        const src = String(md || "").replace(/\r\n/g, "\n").trim();
                        if (!src) return "<p>No description.</p>";

                        const lines = src.split("\n");
                        const out = [];
                        let inCode = false;
                        let codeBuf = [];
                        let listType = null;

                        function closeList() {
                            if (listType) {
                                out.push("</" + listType + ">");
                                listType = null;
                            }
                        }

                        for (let i = 0; i < lines.length; i++) {
                            const rawLine = lines[i];
                            const trimmed = rawLine.trim();

                            if (trimmed.startsWith("```")) {
                                closeList();
                                if (inCode) {
                                    out.push("<pre><code>" + escapeHtml(codeBuf.join("\n")) + "</code></pre>");
                                    codeBuf = [];
                                    inCode = false;
                                } else {
                                    inCode = true;
                                }
                                continue;
                            }

                            if (inCode) {
                                codeBuf.push(rawLine);
                                continue;
                            }

                            if (!trimmed) {
                                closeList();
                                continue;
                            }

                            if (/^([-*_])\1\1+$/.test(trimmed)) {
                                closeList();
                                out.push("<hr/>");
                                continue;
                            }

                            const heading = trimmed.match(/^(#{1,4})\s+(.+)$/);
                            if (heading) {
                                closeList();
                                const level = Math.min(4, Math.max(2, heading[1].length));
                                out.push("<h" + level + ">" + formatInline(heading[2]) + "</h" + level + ">");
                                continue;
                            }

                            const ulMatch = trimmed.match(/^[-*+]\s+(.+)$/);
                            if (ulMatch) {
                                if (listType !== "ul") {
                                    closeList();
                                    listType = "ul";
                                    out.push("<ul>");
                                }
                                out.push("<li>" + formatInline(ulMatch[1]) + "</li>");
                                continue;
                            }

                            const olMatch = trimmed.match(/^\d+\.\s+(.+)$/);
                            if (olMatch) {
                                if (listType !== "ol") {
                                    closeList();
                                    listType = "ol";
                                    out.push("<ol>");
                                }
                                out.push("<li>" + formatInline(olMatch[1]) + "</li>");
                                continue;
                            }

                            if (trimmed.startsWith(">")) {
                                closeList();
                                out.push("<blockquote>" + formatInline(trimmed.replace(/^>\s?/, "")) + "</blockquote>");
                                continue;
                            }

                            closeList();
                            out.push("<p>" + formatInline(trimmed) + "</p>");
                        }

                        if (inCode && codeBuf.length) {
                            out.push("<pre><code>" + escapeHtml(codeBuf.join("\n")) + "</code></pre>");
                        }
                        closeList();
                        return out.join("\n");
                    }

                    const releases = $jsonData;
                    let html = "";

                    releases.forEach(function(r, index) {
                        const badge = r.prerelease
                            ? '<span class="badge badge-pre">BETA</span>'
                            : '<span class="badge badge-stable">STABLE</span>';
                        html += "<h2>" + escapeHtml(r.tag_name || "") + badge + "</h2>";

                        if (r.published_at) {
                            const date = new Date(r.published_at);
                            if (!isNaN(date.getTime())) {
                                html += '<span class="date">' + escapeHtml(date.toLocaleDateString() + " " + date.toLocaleTimeString()) + "</span>";
                            }
                        }

                        html += renderMarkdown(r.body || "No description.");

                        if (index < releases.length - 1) {
                            html += "<hr/>";
                        }
                    });

                    document.getElementById("content").innerHTML = html;
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
