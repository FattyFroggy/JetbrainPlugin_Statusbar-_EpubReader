package dev.ereader.rider.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class NextPageAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.requireBook()?.nextPage()
    }
}
