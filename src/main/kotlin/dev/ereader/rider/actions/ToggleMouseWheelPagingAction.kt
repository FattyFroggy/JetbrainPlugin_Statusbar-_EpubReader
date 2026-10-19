package dev.ereader.rider.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import dev.ereader.rider.service.EReaderService

class ToggleMouseWheelPagingAction : ToggleAction() {
    override fun isSelected(event: AnActionEvent): Boolean {
        val project = event.project ?: return false
        return EReaderService.getInstance(project).isMouseWheelPagingEnabled
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
        val project = event.project ?: return
        EReaderService.getInstance(project).setMouseWheelPagingEnabled(state)
    }
}
