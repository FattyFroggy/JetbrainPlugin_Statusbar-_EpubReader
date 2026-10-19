package dev.ereader.rider.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import dev.ereader.rider.service.EReaderService

class ToggleHoverTextAction : ToggleAction() {
    override fun isSelected(event: AnActionEvent): Boolean {
        val project = event.project ?: return true
        return EReaderService.getInstance(project).isHoverTextEnabled
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
        val project = event.project ?: return
        EReaderService.getInstance(project).setHoverTextEnabled(state)
    }
}
