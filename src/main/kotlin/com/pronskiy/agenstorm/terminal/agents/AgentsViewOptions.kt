package com.pronskiy.agenstorm.terminal.agents

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAwareToggleAction
import com.pronskiy.agenstorm.core.AgenstormBundle
import com.pronskiy.agenstorm.terminal.agents.AgentsSidebarState.Grouping
import com.pronskiy.agenstorm.terminal.agents.AgentsSidebarState.Layout
import com.pronskiy.agenstorm.terminal.agents.AgentsSidebarState.Shown

/**
 * Step X6.3. The *View Options* dropdown in the Agents sidebar's title bar: what to show — running sessions only, or the
 * past ones too — and how to group them, by project or by status (decision 107). Both are the shared layout's
 * ([AgentsSidebarState]), so every window shows the same; a choice is the user's own, whichever window it is made in.
 * Made in code, so it stays out of Find Action like the row menu's *Copy Session ID*.
 */
class AgentsViewOptions : DefaultActionGroup(AgenstormBundle.messagePointer("agents.view.options"), true) {

    init {
        templatePresentation.icon = AllIcons.Actions.Show
        add(Separator.create(AgenstormBundle.message("agents.view.show")))
        add(Choice("agents.view.show.active", { it.show == Shown.ACTIVE }) { it.show = Shown.ACTIVE })
        add(Choice("agents.view.show.all", { it.show == Shown.ALL }) { it.show = Shown.ALL })
        add(Separator.create(AgenstormBundle.message("agents.view.groupBy")))
        add(Choice("agents.view.groupBy.project", { it.groupBy == Grouping.PROJECT }) { it.groupBy = Grouping.PROJECT })
        add(Choice("agents.view.groupBy.status", { it.groupBy == Grouping.STATUS }) { it.groupBy = Grouping.STATUS })
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    /** One radio choice: ticked while [chosen] holds, and choosing it [apply]s it; unticking the ticked one keeps it. */
    private class Choice(key: String, private val chosen: (Layout) -> Boolean, private val apply: (Layout) -> Unit) :
        DumbAwareToggleAction(AgenstormBundle.messagePointer(key)) {

        override fun getActionUpdateThread() = ActionUpdateThread.BGT

        override fun isSelected(e: AnActionEvent): Boolean = chosen(AgentsSidebarState.getInstance().layout.value)

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (state) AgentsSidebarState.getInstance().update { it.apply(apply) }
        }
    }
}
