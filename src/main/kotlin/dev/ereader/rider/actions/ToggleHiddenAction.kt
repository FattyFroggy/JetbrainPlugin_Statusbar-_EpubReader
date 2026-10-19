package dev.ereader.rider.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.ereader.rider.service.EReaderService

class ToggleHiddenAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        EReaderService.getInstance(project).toggleHidden()
    }
}
