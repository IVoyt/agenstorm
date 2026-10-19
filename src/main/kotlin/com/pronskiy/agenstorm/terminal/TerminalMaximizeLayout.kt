package com.pronskiy.agenstorm.terminal

import com.intellij.ide.ui.UISettings
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.pronskiy.agenstorm.core.AgenstormBundle
import com.pronskiy.agenstorm.core.AgenstormConfigurable
import com.pronskiy.agenstorm.core.AgenstormNotifications
import com.pronskiy.agenstorm.core.AgenstormSettings

/**
 * Step J2.1. Makes "maximize the terminal" mean *fill the editor's area* rather than *fill the window*.
 *
 * Which of the two it means is the tool window pane's geometry, not something the maximize call can choose:
 * `ToolWindowPane` nests its two splitters according to `UISettings.wideScreenSupport`. With it off — the
 * default — the vertical splitter is the outer one, so a bottom tool window spans the full width and the side
 * tool windows sit above it; growing the terminal takes their height with it. With it on, the terminal lives
 * inside the editor's column and they keep their full height.
 *
 * So the setting is switched on when the toggle maximizes the terminal, and remembered as ours, following
 * `tabs/git/BranchWidgetPlacement.syncNavBar`: a setting the user turned on themselves is never claimed, and only
 * what Agenstorm did is ever undone — when the terminal is un-maximized again and no other window still has its
 * terminal maximized (the setting is one for every window), and when the feature is switched off. A user asked for
 * the first: the layout used to stay widescreen from the first use on, and getting the old one back meant a trip
 * to the settings every time. It happens on *use*, not merely because the feature is enabled — installing the
 * plugin must not rearrange anybody's IDE.
 */
object TerminalMaximizeLayout {

    enum class WideScreenChange { NONE, ENABLED, RESTORED }

    /** Called before maximizing: gives the editor its own column, and says so the first time. */
    fun ensureEditorAreaOnly(project: Project) {
        val settings = AgenstormSettings.getInstance()
        val change = syncWideScreen(featureOn = true, state = settings.state, ui = UISettings.getInstance())
        if (change == WideScreenChange.ENABLED) showNotice(project)
    }

    /**
     * EDT, called once the terminal was un-maximized: gives the layout back if we were the ones who changed it,
     * unless a terminal is still maximized somewhere, which needs it as much as this one did.
     */
    fun restoreAfterUnmaximize(anyStillMaximized: () -> Boolean = ::anyTerminalMaximized) {
        val state = AgenstormSettings.getInstance().state
        if (!state.terminalMaximizeWideScreenByAgenstorm || anyStillMaximized()) return
        syncWideScreen(featureOn = false, state = state, ui = UISettings.getInstance())
    }

    /** Called when the feature is switched off: gives the layout back if we were the ones who changed it. */
    fun restore() {
        syncWideScreen(featureOn = false, state = AgenstormSettings.getInstance().state, ui = UISettings.getInstance())
    }

    private fun anyTerminalMaximized(): Boolean = ProjectManager.getInstance().openProjects.any { project ->
        val terminal = TerminalMaximizeToggleAction.terminalOf(project) ?: return@any false
        TerminalMaximizeToggleAction.stateOf(project, terminal).isTerminalMaximized
    }

    /**
     * Pure enough to test: turns the widescreen layout on for [featureOn], and off again only when [state]
     * records that Agenstorm was the one that turned it on. Returns what it changed.
     */
    fun syncWideScreen(featureOn: Boolean, state: AgenstormSettings.State, ui: UISettings): WideScreenChange {
        if (featureOn) {
            // Already on: the user's own layout, so it stays theirs and we never claim it.
            if (ui.wideScreenSupport) return WideScreenChange.NONE
            ui.wideScreenSupport = true
            state.terminalMaximizeWideScreenByAgenstorm = true
            ui.fireUISettingsChanged()
            return WideScreenChange.ENABLED
        }
        if (!state.terminalMaximizeWideScreenByAgenstorm) return WideScreenChange.NONE
        state.terminalMaximizeWideScreenByAgenstorm = false
        // The user has since turned it off (or on again on purpose); either way it is theirs now.
        if (!ui.wideScreenSupport) return WideScreenChange.NONE
        ui.wideScreenSupport = false
        ui.fireUISettingsChanged()
        return WideScreenChange.RESTORED
    }

    /**
     * Says once that a layout setting changed. Written before the balloon is shown, so two windows maximizing
     * at the same moment cannot produce two balloons.
     */
    @Synchronized
    private fun showNotice(project: Project) {
        val settings = AgenstormSettings.getInstance()
        if (settings.state.terminalMaximizeNoticeShown) return
        settings.state.terminalMaximizeNoticeShown = true
        AgenstormNotifications.group()
            .createNotification(
                AgenstormBundle.message("terminal.maximize.notice.title"),
                AgenstormBundle.message("terminal.maximize.notice.content"),
                NotificationType.INFORMATION,
            )
            .addAction(
                NotificationAction.createSimple(AgenstormBundle.message("terminal.maximize.notice.settings")) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, AgenstormConfigurable::class.java)
                },
            )
            .notify(project)
    }
}
