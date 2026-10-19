package dev.ereader.rider.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class EReaderStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = EReaderStatusBarWidget.ID

    override fun getDisplayName(): String = "EReader"

    override fun isAvailable(project: Project): Boolean = !project.isDisposed

    override fun createWidget(project: Project): StatusBarWidget = EReaderStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
