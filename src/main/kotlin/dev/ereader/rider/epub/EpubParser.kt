package dev.ereader.rider.epub

import dev.ereader.rider.model.EpubBook
import dev.ereader.rider.model.EpubChapter
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class EpubParser {
    fun parse(file: File): EpubBook {
        require(file.isFile) { "EPUB file does not exist: ${file.absolutePath}" }

        ZipFile(file).use { zip ->
            val container = zip.xml("META-INF/container.xml")
            val opfPath = container
                .getElementsByTagName("rootfile")
                .itemElement(0)
                ?.getAttribute("full-path")
                ?.takeIf { it.isNotBlank() }
                ?: error("Invalid EPUB: missing OPF rootfile")

            val opf = zip.xml(opfPath)
            val baseDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
            val manifest = opf.getElementsByTagName("item").asElements().associateBy { it.getAttribute("id") }
            val title = opf.getElementsByTagName("dc:title").itemElement(0)?.textContent?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            val tocTitles = zip.readTocTitles(opf, baseDir, manifest)

            val chapters = opf.getElementsByTagName("itemref")
                .asElements()
                .mapNotNull { it.getAttribute("idref").takeIf(String::isNotBlank) }
                .mapNotNull { manifest[it] }
                .mapNotNull { item ->
                    val href = item.getAttribute("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val path = joinZipPath(baseDir, href.substringBefore('#'))
                    val html = zip.getEntry(path)?.let { entry ->
                        zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } ?: return@mapNotNull null

                    val fallbackTitle = chapterTitle(path, html)
                    val text = htmlToText(html)
                    if (text.isBlank()) {
                        null
                    } else {
                        EpubChapter(tocTitles[path] ?: fallbackTitle, text)
                    }
                }

            require(chapters.isNotEmpty()) { "No readable chapters found in ${file.name}" }
            return EpubBook(title, chapters)
        }
    }

    private fun ZipFile.xml(path: String) =
        getInputStream(getEntry(path) ?: error("Invalid EPUB: missing $path")).use { input ->
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }.newDocumentBuilder().parse(input)
        }

    private fun ZipFile.readTocTitles(
        opf: Document,
        baseDir: String,
        manifest: Map<String, Element>,
    ): Map<String, String> {
        val navTitles = readNavTocTitles(baseDir, manifest)
        if (navTitles.isNotEmpty()) return navTitles

        val ncxId = opf.getElementsByTagName("spine")
            .itemElement(0)
            ?.getAttribute("toc")
            ?.takeIf { it.isNotBlank() }
        val ncxItem = ncxId?.let { manifest[it] }
            ?: manifest.values.firstOrNull {
                it.getAttribute("media-type").equals("application/x-dtbncx+xml", ignoreCase = true)
            }
            ?: return emptyMap()

        val ncxHref = ncxItem.getAttribute("href").takeIf { it.isNotBlank() } ?: return emptyMap()
        val ncxPath = joinZipPath(baseDir, ncxHref.substringBefore('#'))
        val ncx = runCatching { xml(ncxPath) }.getOrNull() ?: return emptyMap()

        return ncx.getElementsByTagName("navPoint")
            .asElements()
            .mapNotNull { navPoint ->
                val content = navPoint.getElementsByTagName("content").itemElement(0)
                val title = navPoint.getElementsByTagName("text").itemElement(0)
                    ?.textContent
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val src = content?.getAttribute("src")?.takeIf { it.isNotBlank() }
                if (title == null || src == null) null else joinZipPath(baseDir, src.substringBefore('#')) to title
            }
            .toMap()
    }

    private fun ZipFile.readNavTocTitles(
        baseDir: String,
        manifest: Map<String, Element>,
    ): Map<String, String> {
        val navItem = manifest.values.firstOrNull {
            it.getAttribute("properties").split(Regex("\\s+")).any { property -> property == "nav" }
        } ?: return emptyMap()

        val href = navItem.getAttribute("href").takeIf { it.isNotBlank() } ?: return emptyMap()
        val navPath = joinZipPath(baseDir, href.substringBefore('#'))
        val nav = runCatching { xml(navPath) }.getOrNull() ?: return emptyMap()
        val navBaseDir = navPath.substringBeforeLast('/', missingDelimiterValue = "")
        val tocNav = nav.getElementsByTagName("nav")
            .asElements()
            .firstOrNull { it.hasTokenAttribute("epub:type", "toc") || it.hasTokenAttribute("role", "doc-toc") }
            ?: nav.getElementsByTagName("nav").itemElement(0)
            ?: nav.documentElement
        val anchors = tocNav.getElementsByTagName("a").asElements()

        return anchors.mapNotNull { anchor ->
            val title = anchor.textContent.trim().takeIf { it.isNotBlank() }
            val target = anchor.getAttribute("href").takeIf { it.isNotBlank() }
            if (title == null || target == null) null else joinZipPath(navBaseDir, target.substringBefore('#')) to title
        }.toMap()
    }

    private fun htmlToText(html: String): String {
        return html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p\\s*>"), "\n")
            .replace(Regex("(?i)</h[1-6]\\s*>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .decodeHtmlEntities()
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex("\\n\\s+"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun String.decodeHtmlEntities(): String =
        replace("&middot;", "\u00B7")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.toCharReference() ?: match.value
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toIntOrNull(16)?.toCharReference() ?: match.value
            }

    private fun Int.toCharReference(): String =
        takeIf { Character.isValidCodePoint(it) }
            ?.let { String(Character.toChars(it)) }
            ?: ""

    private fun chapterTitle(path: String, html: String): String {
        val heading = Regex("(?is)<h[1-6][^>]*>(.*?)</h[1-6]>")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.let(::htmlToText)
            ?.takeIf { it.isUsableChapterTitle() }
        if (heading != null) return heading.take(80)

        val bodyText = Regex("(?is)<body[^>]*>(.*?)</body>")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.let(::htmlToText)
            ?: htmlToText(html)
        val firstLine = bodyText.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        return firstLine?.take(80)?.takeIf { it.isUsableChapterTitle() }
            ?: path.substringAfterLast('/').substringBeforeLast('.')
    }

    private fun String.isUsableChapterTitle(): Boolean =
        length >= 2 && !matches(Regex("(?i)chapter\\s+\\d+[-_]\\d+"))

    private fun joinZipPath(baseDir: String, relative: String): String {
        val raw = if (baseDir.isBlank()) relative else "$baseDir/$relative"
        val stack = ArrayDeque<String>()
        raw.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> {
                    if (stack.isNotEmpty()) stack.removeLast()
                }
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> =
        (0 until length).mapNotNull { itemElement(it) }

    private fun org.w3c.dom.NodeList.itemElement(index: Int): Element? = item(index) as? Element

    private fun Element.hasTokenAttribute(name: String, token: String): Boolean =
        getAttribute(name).split(Regex("\\s+")).any { it.equals(token, ignoreCase = true) }
}
