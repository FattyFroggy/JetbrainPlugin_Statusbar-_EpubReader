package dev.ereader.rider.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
import dev.ereader.rider.service.EReaderService
import java.awt.datatransfer.StringSelection

class CopyCurrentPageAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val text = EReaderService.getInstance(project).currentPageText()
        if (text == null) {
            Messages.showInfoMessage(project, "Open an EPUB first.", "EReader")
            return
        }
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }
}
