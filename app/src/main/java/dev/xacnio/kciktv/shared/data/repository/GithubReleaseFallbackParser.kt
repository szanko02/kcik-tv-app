package dev.xacnio.kciktv.shared.data.repository

import dev.xacnio.kciktv.shared.data.model.GithubAsset
import dev.xacnio.kciktv.shared.data.model.GithubRelease
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.net.URLDecoder

object GithubReleaseFallbackParser {

    private val PRERELEASE_TAG_REGEX = Regex(
        "(?i)(?:^|[-._+])(alpha|beta|rc|pre|preview|dev|canary)(?:[-._+0-9]|$)|(?i)-(alpha|beta|rc|pre)\\d*"
    )

    fun isPrereleaseTag(tag: String): Boolean = PRERELEASE_TAG_REGEX.containsMatchIn(tag)

    fun buildFallbackAsset(owner: String, repo: String, tag: String): GithubAsset {
        val cleanTag = tag.trim()
        val versionWithoutPrefix = cleanTag.removePrefix("v").removePrefix("V")
        val fileName = "KCIKTV-$versionWithoutPrefix.apk"
        return GithubAsset(
            name = fileName,
            downloadUrl = "https://github.com/$owner/$repo/releases/download/$cleanTag/$fileName",
            size = 0L
        )
    }

    fun parseAtomFeed(atomXml: String, owner: String, repo: String): List<GithubRelease> {
        if (atomXml.isBlank()) return emptyList()
        val doc = Jsoup.parse(atomXml, "https://github.com", Parser.xmlParser())
        val entries = doc.select("entry")
        if (entries.isEmpty()) return emptyList()

        return entries.mapNotNull { entry ->
            val tag = extractTagFromEntry(entry)
            if (tag.isBlank()) return@mapNotNull null

            val title = entry.selectFirst("title")?.text()?.trim().takeUnless { it.isNullOrEmpty() } ?: tag
            val publishedAt = entry.selectFirst("updated")?.text()?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: entry.selectFirst("published")?.text()?.trim().orEmpty()

            val rawContentHtml = extractEntryContentHtml(entry)
            val markdownBody = htmlToMarkdown(rawContentHtml)

            GithubRelease(
                tagName = tag,
                name = title,
                prerelease = isPrereleaseTag(tag),
                publishedAt = publishedAt,
                assets = listOf(buildFallbackAsset(owner, repo, tag)),
                body = markdownBody
            )
        }
    }

    fun parseExpandedAssets(html: String, owner: String, repo: String, tag: String): List<GithubAsset> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, "https://github.com")
        val downloadPrefix = "/$owner/$repo/releases/download/"
        val seenUrls = LinkedHashSet<String>()
        val result = ArrayList<GithubAsset>()

        for (anchor in doc.select("a[href]")) {
            val rawHref = anchor.attr("href").trim()
            if (rawHref.isEmpty()) continue

            val pathPart = rawHref.substringBefore("?").substringBefore("#")
            if (!pathPart.endsWith(".apk", ignoreCase = true)) continue
            if (!pathPart.contains(downloadPrefix, ignoreCase = true) &&
                !pathPart.contains("/releases/download/", ignoreCase = true)
            ) {
                continue
            }

            val fullUrl = when {
                rawHref.startsWith("https://", ignoreCase = true) ||
                    rawHref.startsWith("http://", ignoreCase = true) -> rawHref
                rawHref.startsWith("/") -> "https://github.com$rawHref"
                else -> "https://github.com/$rawHref"
            }

            if (!seenUrls.add(fullUrl)) continue

            val anchorText = anchor.text().trim()
            val fileName = if (anchorText.endsWith(".apk", ignoreCase = true)) {
                anchorText
            } else {
                decodeUrlSegment(pathPart.substringAfterLast('/'))
            }
            if (fileName.isBlank()) continue

            result.add(
                GithubAsset(
                    name = fileName,
                    downloadUrl = fullUrl,
                    size = 0L
                )
            )
        }

        return result
    }

    fun resolveAssetsOrFallback(
        expandedAssetsHtml: String?,
        owner: String,
        repo: String,
        tag: String
    ): List<GithubAsset> {
        val parsed = if (!expandedAssetsHtml.isNullOrBlank()) {
            parseExpandedAssets(expandedAssetsHtml, owner, repo, tag)
        } else {
            emptyList()
        }
        return parsed.ifEmpty { listOf(buildFallbackAsset(owner, repo, tag)) }
    }

    private fun extractTagFromEntry(entry: Element): String {
        for (link in entry.select("link[href]")) {
            val href = link.attr("href").trim()
            val marker = "/releases/tag/"
            val idx = href.indexOf(marker, ignoreCase = true)
            if (idx != -1) {
                val rawTag = href.substring(idx + marker.length)
                    .substringBefore("?")
                    .substringBefore("#")
                    .trim('/')
                val decoded = decodeUrlSegment(rawTag)
                if (decoded.isNotBlank()) return decoded
            }
        }

        val idText = entry.selectFirst("id")?.text()?.trim().orEmpty()
        if (idText.contains("/Repository/")) {
            val afterRepo = idText.substringAfter("/Repository/").substringAfter('/', "")
            val decoded = decodeUrlSegment(afterRepo)
            if (decoded.isNotBlank()) return decoded
        }
        return ""
    }

    private fun extractEntryContentHtml(entry: Element): String {
        val contentEl = entry.selectFirst("content") ?: return ""
        return if (contentEl.children().isNotEmpty() && contentEl.ownText().isBlank()) {
            contentEl.html()
        } else {
            contentEl.text()
        }
    }

    internal fun htmlToMarkdown(html: String): String {
        if (html.isBlank()) return ""
        val doc = Jsoup.parseBodyFragment(html)
        val body = doc.body()
        val rawText = body.text().trim()
        if (rawText.isEmpty() || rawText.equals("No content.", ignoreCase = true)) {
            return ""
        }

        val lines = ArrayList<String>()
        renderBlockChildren(body, lines)
        return lines.joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun renderBlockChildren(parent: Element, out: MutableList<String>) {
        for (node in parent.childNodes()) {
            when (node) {
                is TextNode -> {
                    val text = node.text().trim()
                    if (text.isNotEmpty()) out.add(text)
                }
                is Element -> {
                    when (node.tagName().lowercase()) {
                        "h1" -> out.add("# ${renderInline(node).trim()}")
                        "h2" -> out.add("## ${renderInline(node).trim()}")
                        "h3" -> out.add("### ${renderInline(node).trim()}")
                        "h4", "h5", "h6" -> out.add("#### ${renderInline(node).trim()}")
                        "p" -> {
                            val text = renderInline(node).trim()
                            if (text.isNotEmpty()) {
                                out.add(text)
                                out.add("")
                            }
                        }
                        "ul" -> {
                            for (li in node.children()) {
                                if (!li.tagName().equals("li", ignoreCase = true)) continue
                                val itemText = renderInline(li).trim()
                                if (itemText.isNotEmpty()) out.add("- $itemText")
                            }
                            out.add("")
                        }
                        "ol" -> {
                            var idx = 1
                            for (li in node.children()) {
                                if (!li.tagName().equals("li", ignoreCase = true)) continue
                                val itemText = renderInline(li).trim()
                                if (itemText.isNotEmpty()) {
                                    out.add("$idx. $itemText")
                                    idx++
                                }
                            }
                            out.add("")
                        }
                        "pre" -> {
                            val code = node.wholeText().trimEnd()
                            if (code.isNotEmpty()) {
                                out.add("```")
                                out.add(code)
                                out.add("```")
                                out.add("")
                            }
                        }
                        "blockquote" -> {
                            val quote = renderInline(node).trim()
                            if (quote.isNotEmpty()) {
                                quote.lines().forEach { line -> out.add("> ${line.trim()}") }
                                out.add("")
                            }
                        }
                        "hr" -> {
                            out.add("---")
                            out.add("")
                        }
                        else -> renderBlockChildren(node, out)
                    }
                }
            }
        }
    }

    private fun renderInline(parent: Node): String {
        val sb = StringBuilder()
        for (child in parent.childNodes()) {
            when (child) {
                is TextNode -> sb.append(child.text())
                is Element -> {
                    when (child.tagName().lowercase()) {
                        "strong", "b" -> {
                            val inner = renderInline(child).trim()
                            if (inner.isNotEmpty()) sb.append("**").append(inner).append("**")
                        }
                        "em", "i" -> {
                            val inner = renderInline(child).trim()
                            if (inner.isNotEmpty()) sb.append("*").append(inner).append("*")
                        }
                        "code" -> {
                            val inner = child.text()
                            if (inner.isNotEmpty()) sb.append('`').append(inner).append('`')
                        }
                        "br" -> sb.append('\n')
                        "a" -> {
                            val label = renderInline(child).trim()
                            val href = child.attr("href").trim()
                            if (href.isNotEmpty() && label.isNotEmpty() && label != href) {
                                sb.append('[').append(label).append("](").append(href).append(')')
                            } else {
                                sb.append(label.ifEmpty { href })
                            }
                        }
                        "ul", "ol" -> {
                            for (li in child.children()) {
                                if (!li.tagName().equals("li", ignoreCase = true)) continue
                                val itemText = renderInline(li).trim()
                                if (itemText.isNotEmpty()) {
                                    sb.append("\n  - ").append(itemText)
                                }
                            }
                        }
                        else -> sb.append(renderInline(child))
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun decodeUrlSegment(segment: String): String =
        runCatching { URLDecoder.decode(segment, "UTF-8") }.getOrDefault(segment)
}
