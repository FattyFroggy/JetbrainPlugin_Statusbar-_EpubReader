package dev.ereader.rider.service

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.ereader.rider.epub.EpubParser
import dev.ereader.rider.model.EpubBook
import dev.ereader.rider.model.ReaderPosition
import java.io.File

@State(
    name = "EReaderState",
    storages = [Storage("ereader.xml")],
)
@Service(Service.Level.PROJECT)
class EReaderService : PersistentStateComponent<EReaderService.State> {
    private val listeners = mutableListOf<Runnable>()
    private var book: EpubBook? = null
    private var position = ReaderPosition()
    private var hidden = false
    private var highlightEnabled = true
    private var hoverTextEnabled = true
    private var mouseWheelPagingEnabled = false
    private var bookPath: String? = null
    private var pageLength = DEFAULT_PAGE_LENGTH

    val currentBook: EpubBook?
        get() = book

    val isHidden: Boolean
        get() = hidden

    val isHighlightEnabled: Boolean
        get() = highlightEnabled

    val isHoverTextEnabled: Boolean
        get() = hoverTextEnabled

    val isMouseWheelPagingEnabled: Boolean
        get() = mouseWheelPagingEnabled

    val currentChapterIndex: Int
        get() = position.chapterIndex

    override fun getState(): State = State(
        bookPath = bookPath,
        chapterIndex = position.chapterIndex,
        pageIndex = position.pageIndex,
        chapterOffset = pageStartOffset(position.chapterIndex, position.pageIndex),
        hidden = hidden,
        highlightEnabled = highlightEnabled,
        hoverTextEnabled = hoverTextEnabled,
        mouseWheelPagingEnabled = mouseWheelPagingEnabled,
    )

    override fun loadState(state: State) {
        bookPath = state.bookPath
        position = ReaderPosition(state.chapterIndex.coerceAtLeast(0), state.pageIndex.coerceAtLeast(0))
        hidden = state.hidden
        highlightEnabled = state.highlightEnabled
        hoverTextEnabled = state.hoverTextEnabled
        mouseWheelPagingEnabled = state.mouseWheelPagingEnabled
        reloadRememberedBook()
        val savedOffset = state.chapterOffset?.coerceAtLeast(0)
        clampPosition(savedOffset)
    }

    fun open(file: File) {
        book = EpubParser().parse(file)
        bookPath = file.absolutePath
        position = ReaderPosition()
        hidden = false
        notifyChanged()
    }

    fun nextPage() {
        if (hidden) return
        val pages = pagesForCurrentChapter() ?: return
        val currentBook = book ?: return
        position = when {
            position.pageIndex < pages.lastIndex -> position.copy(pageIndex = position.pageIndex + 1)
            position.chapterIndex < currentBook.chapters.lastIndex -> ReaderPosition(position.chapterIndex + 1, 0)
            else -> position
        }
        notifyChanged()
    }

    fun previousPage() {
        if (hidden) return
        val currentBook = book ?: return
        position = when {
            position.pageIndex > 0 -> position.copy(pageIndex = position.pageIndex - 1)
            position.chapterIndex > 0 -> {
                val previousChapter = position.chapterIndex - 1
                ReaderPosition(previousChapter, pagesForChapter(previousChapter).lastIndex.coerceAtLeast(0))
            }
            else -> position
        }
        notifyChanged()
    }

    fun chooseChapter(index: Int) {
        val currentBook = book ?: return
        if (index !in currentBook.chapters.indices) return
        position = ReaderPosition(index, 0)
        hidden = false
        notifyChanged()
    }

    fun toggleHidden() {
        hidden = !hidden
        notifyChanged()
    }

    fun setHighlightEnabled(enabled: Boolean) {
        if (highlightEnabled == enabled) return
        highlightEnabled = enabled
        notifyChanged()
    }

    fun setHoverTextEnabled(enabled: Boolean) {
        if (hoverTextEnabled == enabled) return
        hoverTextEnabled = enabled
        notifyChanged()
    }

    fun setMouseWheelPagingEnabled(enabled: Boolean) {
        if (mouseWheelPagingEnabled == enabled) return
        mouseWheelPagingEnabled = enabled
        notifyChanged()
    }

    fun updatePageLength(length: Int, notify: Boolean = true) {
        val normalizedLength = length.coerceIn(MIN_PAGE_LENGTH, MAX_PAGE_LENGTH)
        if (pageLength == normalizedLength) return
        val currentPageStart = pageStartOffset(position.chapterIndex, position.pageIndex)
        pageLength = normalizedLength
        clampPosition(currentPageStart)
        if (notify) notifyChanged()
    }

    fun statusText(): String {
        if (hidden) return ""
        val currentBook = book ?: return "EReader: no book"
        val pages = pagesForCurrentChapter() ?: return "EReader: no page"
        val page = pages.getOrNull(position.pageIndex).orEmpty()
        return statusPrefixText(currentBook, pages) + page
    }

    fun currentPageText(): String? {
        if (book == null) return null
        return pagesForCurrentChapter()?.getOrNull(position.pageIndex).orEmpty()
    }

    fun statusPrefixText(): String {
        if (hidden) return ""
        val currentBook = book ?: return "EReader: no book"
        val pages = pagesForCurrentChapter() ?: return "EReader: no page"
        return statusPrefixText(currentBook, pages)
    }

    fun addListener(listener: Runnable) {
        listeners += listener
    }

    fun removeListener(listener: Runnable) {
        listeners -= listener
    }

    private fun pagesForCurrentChapter(): List<String>? = pagesForChapter(position.chapterIndex)

    private fun pagesForChapter(chapterIndex: Int): List<String> {
        val chapter = book?.chapters?.getOrNull(chapterIndex) ?: return emptyList()
        return chapter.text.chunkedByDisplayLength(pageLength)
    }

    private fun totalProgressPercent(): String {
        val currentBook = book ?: return "0.00"
        val totalPages = currentBook.chapters.indices.sumOf { pagesForChapter(it).size }.coerceAtLeast(1)
        val readPagesBeforeChapter = (0 until position.chapterIndex).sumOf { pagesForChapter(it).size }
        val currentPage = (readPagesBeforeChapter + position.pageIndex + 1).coerceIn(1, totalPages)
        return formatPercent(currentPage, totalPages)
    }

    private fun chapterProgressPercent(pages: List<String>): String {
        val totalPages = pages.size.coerceAtLeast(1)
        val currentPage = (position.pageIndex + 1).coerceIn(1, totalPages)
        return formatPercent(currentPage, totalPages)
    }

    private fun formatPercent(current: Int, total: Int): String =
        String.format(java.util.Locale.ROOT, "%.2f", current * 100.0 / total)

    private fun statusPrefixText(currentBook: EpubBook, pages: List<String>): String {
        val chapter = currentBook.chapters.getOrNull(position.chapterIndex)
        val chapterTitle = chapter?.title?.takeIf { it.isNotBlank() } ?: "Chapter ${position.chapterIndex + 1}"
        return "${currentBook.title}(${totalProgressPercent()}%)$chapterTitle(${chapterProgressPercent(pages)}%): "
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it.run() }
    }

    private fun reloadRememberedBook() {
        val rememberedPath = bookPath ?: return
        val file = File(rememberedPath)
        book = if (file.isFile) runCatching { EpubParser().parse(file) }.getOrNull() else null
    }

    private fun clampPosition(preferredChapterOffset: Int? = null) {
        val currentBook = book ?: return
        val chapterIndex = position.chapterIndex.coerceIn(0, currentBook.chapters.lastIndex.coerceAtLeast(0))
        val pages = pagesForChapter(chapterIndex)
        val pageIndex = preferredChapterOffset
            ?.let { pageIndexForOffset(chapterIndex, it) }
            ?: position.pageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        position = ReaderPosition(chapterIndex, pageIndex)
    }

    private fun pageStartOffset(chapterIndex: Int, pageIndex: Int): Int {
        val chapter = book?.chapters?.getOrNull(chapterIndex) ?: return 0
        return chapter.text.pageRangesByDisplayLength(pageLength)
            .getOrNull(pageIndex)
            ?.first
            ?: 0
    }

    private fun pageIndexForOffset(chapterIndex: Int, offset: Int): Int {
        val chapter = book?.chapters?.getOrNull(chapterIndex) ?: return 0
        val ranges = chapter.text.pageRangesByDisplayLength(pageLength)
        if (ranges.isEmpty()) return 0
        return ranges.indexOfLast { offset >= it.first }.coerceAtLeast(0)
    }

    private fun String.chunkedByDisplayLength(maxLength: Int): List<String> {
        val normalized = replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return listOf("")

        return normalized.pageRangesByDisplayLength(maxLength).map { range ->
            normalized.substring(range.first, range.last + 1)
        }
    }

    private fun String.pageRangesByDisplayLength(maxLength: Int): List<IntRange> {
        val normalized = replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return listOf(0..0)

        val result = mutableListOf<IntRange>()
        var index = 0
        while (index < normalized.length) {
            val end = (index + maxLength).coerceAtMost(normalized.length)
            val preferredEnd = normalized.lastIndexOf(' ', end - 1).takeIf { it > index + maxLength / 2 } ?: end
            result += index until preferredEnd
            index = preferredEnd
            while (index < normalized.length && normalized[index].isWhitespace()) index++
        }
        return result
    }

    data class State(
        var bookPath: String? = null,
        var chapterIndex: Int = 0,
        var pageIndex: Int = 0,
        var chapterOffset: Int? = null,
        var hidden: Boolean = false,
        var highlightEnabled: Boolean = true,
        var hoverTextEnabled: Boolean = true,
        var mouseWheelPagingEnabled: Boolean = false,
    )

    companion object {
        private const val DEFAULT_PAGE_LENGTH = 180
        private const val MIN_PAGE_LENGTH = 1
        private const val MAX_PAGE_LENGTH = 500

        fun getInstance(project: Project): EReaderService = project.service()
    }
}
