package com.pronskiy.agenstorm.terminal.agents

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.pronskiy.agenstorm.terminal.tmux.TmuxTabs

/**
 * Step X6.1. Which sessions the user can see right now: the one in the tab the Terminal shows in the window in front,
 * while the IDE itself is in front. Nothing else is on screen — another tab, a hidden Terminal, another project's window,
 * another app, a background terminal, a session outside the IDE — and that is where [AttentionTracker] tells.
 */
object OnScreen {

    /** What the window in front shows: whether the IDE is the active app, its Terminal is visible, and the tab it shows. */
    data class Screen<T>(val active: Boolean, val terminalVisible: Boolean, val selectedTab: T?) {
        companion object {
            fun <T> nothing(): Screen<T> = Screen(active = false, terminalVisible = false, selectedTab = null)
        }
    }

    fun <T> ids(places: Map<String, SessionPlace<T>>, screen: Screen<T>): Set<String> {
        if (!screen.active || !screen.terminalVisible) return emptySet()
        val shown = screen.selectedTab ?: return emptySet()
        return places.filterValues { (it as? SessionPlace.InTab)?.tab == shown }.keys
    }

    /**
     * The window in front as it is now, EDT. Its Terminal's content is only looked at once it exists: making it would
     * start the platform's restore of last run's tabs (Epic U).
     */
    fun current(): Screen<ProjectTab> {
        val project = AgentsSidebarSync.front()?.takeIf { !it.isDisposed } ?: return Screen.nothing()
        return runCatching {
            val window = ToolWindowManager.getInstance(project).getToolWindow(TmuxTabs.TOOL_WINDOW) ?: return Screen.nothing()
            val selected = window.contentManagerIfCreated?.selectedContent
            val tab = selected?.let { content -> TerminalToolWindowTabsManager.getInstance(project).tabs.firstOrNull { it.content == content } }
            Screen(ApplicationManager.getApplication().isActive, window.isVisible, tab?.let { ProjectTab(project, it) })
        }.getOrDefault(Screen.nothing())
    }
}
