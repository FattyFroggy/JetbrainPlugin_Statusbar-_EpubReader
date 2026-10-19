package dev.ereader.rider.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.util.ui.JBUI
import dev.ereader.rider.service.EReaderService
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.SwingUtilities
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class EReaderStatusBarWidget(
    project: Project,
) : CustomStatusBarWidget {
    private var statusBar: StatusBar? = null
    private val service = EReaderService.getInstance(project)
    private val label = JLabel().apply {
        border = JBUI.Borders.empty(0, 8)
        foreground = JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND
        font = JBUI.CurrentTheme.StatusBar.defaultFont()
        horizontalAlignment = SwingConstants.LEFT
        toolTipText = "EReader"
    }
    private val component = JPanel(BorderLayout()).apply {
        isOpaque = false
        preferredSize = Dimension(DEFAULT_WIDTH, HEIGHT)
        minimumSize = Dimension(MIN_WIDTH, HEIGHT)
        add(label, BorderLayout.CENTER)
    }
    private var hovered = false
    private var pressed = false
    private val listener = Runnable { updateText() }
    private val resizeListener = object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) {
            updateAvailableLength()
        }
    }
    private val mouseWheelListener = MouseWheelListener { event -> handleMouseWheel(event) }
    private val clickListener = object : MouseAdapter() {
        override fun mouseEntered(event: MouseEvent) {
            updateInteractionState(hovered = true)
        }

        override fun mouseExited(event: MouseEvent) {
            val point = SwingUtilities.convertPoint(event.component, event.point, component)
            if (!component.contains(point)) {
                updateInteractionState(hovered = false)
            }
        }

        override fun mousePressed(event: MouseEvent) {
            if (SwingUtilities.isLeftMouseButton(event)) {
                updateInteractionState(hovered = true, pressed = true)
            }
        }

        override fun mouseReleased(event: MouseEvent) {
            val point = SwingUtilities.convertPoint(event.component, event.point, component)
            updateInteractionState(hovered = component.contains(point))
            if (SwingUtilities.isRightMouseButton(event)) {
                showContextMenu(point.x, point.y)
            }
        }

        override fun mouseClicked(event: MouseEvent) {
            if (SwingUtilities.isLeftMouseButton(event)) {
                service.nextPage()
            }
        }
    }

    init {
        component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        component.addMouseListener(clickListener)
        label.addMouseListener(clickListener)
        component.addMouseWheelListener(mouseWheelListener)
        label.addMouseWheelListener(mouseWheelListener)
        updateText()
        service.addListener(listener)
    }

    override fun ID(): String = ID

    override fun getComponent() = component

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        statusBar.component?.addComponentListener(resizeListener)
        updateAvailableLength()
        updateText()
    }

    override fun dispose() {
        statusBar?.component?.removeComponentListener(resizeListener)
        component.removeMouseListener(clickListener)
        label.removeMouseListener(clickListener)
        component.removeMouseWheelListener(mouseWheelListener)
        label.removeMouseWheelListener(mouseWheelListener)
        service.removeListener(listener)
        statusBar = null
    }

    private fun updateInteractionState(hovered: Boolean, pressed: Boolean = false) {
        this.hovered = hovered
        this.pressed = pressed
        val highlighted = hovered && service.isHighlightEnabled
        component.isOpaque = highlighted
        component.background = when {
            pressed && highlighted -> JBUI.CurrentTheme.StatusBar.Widget.PRESSED_BACKGROUND
            else -> JBUI.CurrentTheme.StatusBar.Widget.HOVER_BACKGROUND
        }
        label.foreground = when {
            pressed && highlighted -> JBUI.CurrentTheme.StatusBar.Widget.PRESSED_FOREGROUND
            highlighted -> JBUI.CurrentTheme.StatusBar.Widget.HOVER_FOREGROUND
            else -> JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND
        }
        component.repaint()
    }

    private fun showContextMenu(x: Int, y: Int) {
        val actionManager = ActionManager.getInstance()
        val actionGroup = actionManager.getAction(ACTION_GROUP_ID) as? ActionGroup ?: return
        actionManager.createActionPopupMenu(POPUP_PLACE, actionGroup)
            .component
            .show(component, x, y)
    }

    private fun handleMouseWheel(event: MouseWheelEvent) {
        if (event.isConsumed || event.isShiftDown || event.wheelRotation == 0) return
        if (!service.isMouseWheelPagingEnabled || service.isHidden || service.currentBook == null) return
        if (!component.isShowing) return

        if (event.wheelRotation < 0) {
            service.previousPage()
        } else {
            service.nextPage()
        }
        event.consume()
    }

    private fun updateText() {
        label.foreground = JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND
        label.font = JBUI.CurrentTheme.StatusBar.defaultFont()
        fitPageToAvailableWidth()
        val text = service.statusText()
        val hidden = service.isHidden
        label.text = text
        val hoverText = text.takeIf { service.isHoverTextEnabled }
        label.toolTipText = hoverText
        component.toolTipText = hoverText
        component.isVisible = !hidden
        component.preferredSize = if (hidden) Dimension(0, HEIGHT) else Dimension(availableWidth(), HEIGHT)
        component.minimumSize = if (hidden) Dimension(0, HEIGHT) else Dimension(MIN_WIDTH, HEIGHT)
        component.maximumSize = if (hidden) Dimension(0, HEIGHT) else Dimension(Int.MAX_VALUE, HEIGHT)
        updateInteractionState(hovered = hovered && !hidden, pressed = pressed && !hidden)
        component.revalidate()
        statusBar?.updateWidget(ID)
    }

    private fun updateAvailableLength() {
        fitPageToAvailableWidth()
        if (!service.isHidden) {
            component.preferredSize = Dimension(availableWidth(), HEIGHT)
            component.revalidate()
        }
    }

    private fun fitPageToAvailableWidth() {
        if (service.isHidden) return

        val width = availableWidth()
        val fontMetrics = label.getFontMetrics(label.font)
        val conservativeCharWidth = maxOf(
            fontMetrics.charWidth('W'),
            fontMetrics.charWidth('\u9605'),
            1,
        )
        val prefixWidth = fontMetrics.stringWidth(service.statusPrefixText())
        val fullTextBudget = (width - label.insets.left - label.insets.right - TEXT_END_SAFETY_WIDTH)
            .coerceAtLeast(conservativeCharWidth)
        val textBudget = (fullTextBudget - prefixWidth)
            .coerceAtLeast(conservativeCharWidth)
        service.updatePageLength((textBudget / conservativeCharWidth).coerceAtLeast(1), notify = false)
        shrinkPageLengthToFit(fullTextBudget)
    }

    private fun shrinkPageLengthToFit(fullTextBudget: Int) {
        val fontMetrics = label.getFontMetrics(label.font)
        var text = service.statusText()
        var attempts = 0

        while (fontMetrics.stringWidth(text) > fullTextBudget && currentPageText().length > 1 && attempts < MAX_FIT_ATTEMPTS) {
            val currentPageText = currentPageText()
            val overflowScale = fullTextBudget.toDouble() / fontMetrics.stringWidth(text)
            val nextLength = (currentPageText.length * overflowScale * FIT_SAFETY_FACTOR)
                .toInt()
                .coerceAtMost(currentPageText.length - 1)
                .coerceAtLeast(1)
            if (nextLength >= currentPageText.length) break

            service.updatePageLength(nextLength, notify = false)
            text = service.statusText()
            attempts++
        }
    }

    private fun currentPageText(): String {
        val prefix = service.statusPrefixText()
        return service.statusText().removePrefix(prefix)
    }

    private fun availableWidth(): Int {
        val statusBarWidth = statusBar?.component?.width?.takeIf { it > 0 } ?: DEFAULT_WIDTH
        return (statusBarWidth - RESERVED_RIGHT_STATUS_WIDTH).coerceIn(MIN_WIDTH, MAX_WIDTH)
    }

    companion object {
        const val ID = "EReaderStatusBarWidget"
        private const val HEIGHT = 24
        private const val MIN_WIDTH = 120
        private const val DEFAULT_WIDTH = 420
        private const val MAX_WIDTH = 1400
        private const val RESERVED_RIGHT_STATUS_WIDTH = 520
        private const val TEXT_END_SAFETY_WIDTH = 24
        private const val FIT_SAFETY_FACTOR = 0.92
        private const val MAX_FIT_ATTEMPTS = 4
        private const val ACTION_GROUP_ID = "EReader.Actions"
        private const val POPUP_PLACE = "EReaderStatusBarPopup"
    }
}
