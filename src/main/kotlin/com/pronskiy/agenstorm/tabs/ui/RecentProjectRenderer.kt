package com.pronskiy.agenstorm.tabs.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

/**
 * The recent projects of the "+" popup, drawn as the stock project widget draws them: the project's icon, its name,
 * its path and the branch it is on. Every other item is left to [base], the popup's own renderer.
 *
 * The branch is asked of [branchOf] on every paint rather than taken from the action. `ReopenProjectAction` keeps the
 * branch it was made with, and Git looks a project's branch up in the background the first time anyone asks, so the
 * actions of a first popup had none and only the next popup showed them (Roman, 2026-10-19).
 */
class RecentProjectRenderer(
    private val base: ListCellRenderer<Any?>,
    private val branchOf: (String) -> String?,
) : ListCellRenderer<Any?> {

    override fun getListCellRendererComponent(list: JList<out Any?>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
        val action = (value as? PopupFactoryImpl.ActionItem)?.action as? ReopenProjectAction
            ?: return base.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        return pane(action, branchOf(action.projectPath) ?: action.branchName, isSelected)
    }

    companion object {
        /** One project: the icon at the top left, then its name, its path and, when known, its branch. */
        fun pane(action: ReopenProjectAction, branch: String?, isSelected: Boolean): JComponent {
            val lines = JPanel(VerticalLayout(JBUI.scale(2))).apply { isOpaque = false }
            lines.add(JBLabel(action.projectNameToDisplay).apply {
                foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else UIUtil.getListForeground()
            })
            action.projectPathToDisplay?.let { lines.add(detail(it)) }
            branch?.let { lines.add(detail(it).apply { icon = IconUtil.colorize(AllIcons.Vcs.Branch, UIUtil.getLabelInfoForeground()) }) }
            val icon = JBLabel(action.projectIcon).apply {
                verticalAlignment = SwingConstants.TOP
                border = JBUI.Borders.emptyRight(8)
            }
            val row = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(6, 0)
                add(icon, BorderLayout.WEST)
                add(lines, BorderLayout.CENTER)
            }
            return SelectablePanel.wrap(row, JBUI.CurrentTheme.Popup.BACKGROUND).apply {
                PopupUtil.configListRendererFlexibleHeight(this)
                if (isSelected) selectionColor = UIUtil.getListSelectionBackground(true)
            }
        }

        private fun detail(text: String) = JBLabel(text).apply {
            font = JBUI.Fonts.smallFont()
            foreground = UIUtil.getLabelInfoForeground()
        }
    }
}
