package dev.ereader.rider.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class EReaderStatusBarInstaller : ProjectActivity {
    override suspend fun execute(project: Project) {
        val statusBar = waitForStatusBar(project) ?: return
        if (statusBar.getWidget(EReaderStatusBarWidget.ID) != null) return

        val widget = EReaderStatusBarWidget(project)
        val disposable = Disposable { statusBar.removeWidget(EReaderStatusBarWidget.ID) }
        Disposer.register(project, disposable)
        Disposer.register(disposable, widget)

        statusBar.addWidgetToLeft(widget)
    }

    private suspend fun waitForStatusBar(project: Project): IdeStatusBarImpl? {
        repeat(STATUS_BAR_LOOKUP_ATTEMPTS) {
            val statusBar = withContext(Dispatchers.EDT) {
                WindowManager.getInstance().getStatusBar(project) as? IdeStatusBarImpl
            }
            if (statusBar != null) return statusBar
            delay(STATUS_BAR_LOOKUP_DELAY_MS)
        }
        return null
    }

    private suspend fun IdeStatusBarImpl.addWidgetToLeft(widget: StatusBarWidget) {
        withContext(Dispatchers.EDT) {
            val position = Class.forName("com.intellij.openapi.wm.impl.status.Position")
                .enumConstants
                .first { it.toString() == "LEFT" }
            val method = IdeStatusBarImpl::class.java.getMethod(
                "access\$addWidget",
                IdeStatusBarImpl::class.java,
                StatusBarWidget::class.java,
                position.javaClass,
                LoadingOrder::class.java,
            )
            method.invoke(null, this@addWidgetToLeft, widget, position, LoadingOrder.ANY)
        }
    }

    companion object {
        private const val STATUS_BAR_LOOKUP_ATTEMPTS = 50
        private const val STATUS_BAR_LOOKUP_DELAY_MS = 100L
    }
}
