package dev.ereader.rider.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.Messages
import java.io.File

class OpenBookAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val service = event.readerService() ?: return
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withFileFilter { it.extension.equals("epub", ignoreCase = true) }
            .withTitle("Open EPUB")
            .withDescription("Choose an EPUB file to read in the Rider status bar")

        val file = FileChooser.chooseFile(descriptor, event.project, null) ?: return
        try {
            service.open(File(file.path))
        } catch (error: Throwable) {
            Messages.showErrorDialog(event.project, error.message ?: "Failed to open EPUB.", "EReader")
        }
    }
}
