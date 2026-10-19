package dev.ereader.rider.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.Consumer
import javax.swing.JList
import javax.swing.SwingUtilities

class ChooseChapterAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val service = event.requireBook() ?: return
        val book = service.currentBook ?: return
        val items = book.chapters.mapIndexed { index, chapter ->
            ChapterListItem(index, chapter.title.ifBlank { "Chapter ${index + 1}" })
        }
        val currentChapterIndex = service.currentChapterIndex.coerceIn(items.indices)
        val list = JBList(items).apply {
            visibleRowCount = items.size.coerceIn(1, 16)
            selectedIndex = currentChapterIndex
            cellRenderer = object : ColoredListCellRenderer<ChapterListItem>() {
                override fun customizeCellRenderer(
                    list: JList<out ChapterListItem>,
                    value: ChapterListItem?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                ) {
                    if (value == null) return
                    append("${value.index + 1}. ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(value.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
            }
        }

        val popup = JBPopupFactory.getInstance()
            .createListPopupBuilder(list)
            .setTitle(book.title)
            .setItemChosenCallback(Consumer<ChapterListItem> {
                val selectedIndex = list.selectedIndex
                if (selectedIndex >= 0) service.chooseChapter(selectedIndex)
            })
            .createPopup()

        popup.showCenteredInCurrentWindow(project)
        SwingUtilities.invokeLater {
            list.selectedIndex = currentChapterIndex
            list.ensureIndexIsVisible(currentChapterIndex)
        }
    }

    private data class ChapterListItem(val index: Int, val title: String)
}
