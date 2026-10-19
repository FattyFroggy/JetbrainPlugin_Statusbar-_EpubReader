package dev.ereader.rider.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import dev.ereader.rider.service.EReaderService

internal fun AnActionEvent.readerService(): EReaderService? {
    val project = project
    if (project == null) {
        Messages.showWarningDialog("Open a project before using EReader.", "EReader")
        return null
    }
    return EReaderService.getInstance(project)
}

internal fun AnActionEvent.requireBook(): EReaderService? {
    val service = readerService() ?: return null
    if (service.currentBook == null) {
        Messages.showInfoMessage(project, "Open an EPUB first.", "EReader")
        return null
    }
    return service
}
