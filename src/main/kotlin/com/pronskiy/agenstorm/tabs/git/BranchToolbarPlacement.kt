package com.pronskiy.agenstorm.tabs.git

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Anchor
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.logger
import com.pronskiy.agenstorm.core.AgenstormPlugin
import com.pronskiy.agenstorm.core.AgenstormSettings

/**
 * The Git branch at the top, before the project tabs: opt-in, for people used to the stock IDE's branch at the top left
 * (a few users asked; Roman, 2026-10-19, decision 111). The status bar stays the default.
 *
 * The toolbar's VCS group — the stock branch widget, under our [VcsToolbarGroup] — is moved to the front of the stock
 * `MainToolbarLeft`, ahead of `main.toolbar.Project`, which the project tabs occupy. The ≡ button is the frame's own,
 * outside the group, so the bar reads ≡ · branch · tabs · +, and the branch stays put however many tabs are open. The
 * group is moved, not replaced, the way `mainmenu/MainMenuFolder` moves menus (decision 109): every id keeps its
 * instance. It goes back after the entry it followed when the branch returns to the status bar, the project tabs go off
 * or Agenstorm is unloaded; the move lives in memory only, so a restart begins from the stock order.
 *
 * EDT only: the toolbar's groups are shared by every window.
 */
object BranchToolbarPlacement {

    private val LOG = logger<BranchToolbarPlacement>()

    const val LEFT_ID = "MainToolbarLeft"
    const val PROJECT_ID = "main.toolbar.Project"

    /** The entry the VCS group followed before it was moved; null while it is where the platform put it. */
    private var after: String? = null

    /** Follows the settings. Idempotent, so every entry point can just call it. */
    fun sync() = sync(isWanted(AgenstormSettings.getInstance().state))

    fun isWanted(state: AgenstormSettings.State): Boolean = state.projectTabsEnabled && !state.branchInStatusBar

    @Synchronized
    fun sync(first: Boolean) {
        if (first) moveFirst() else putBack()
    }

    private fun moveFirst() {
        val actionManager = ActionManager.getInstance()
        val left = left(actionManager) ?: return
        val vcs = actionManager.getAction(VcsToolbarGroup.ID) ?: return
        val children = left.getChildren(actionManager)
        val index = children.indexOfFirst { it === vcs }
        // Not in the group (no Git plugin, or someone took it out), or first already.
        if (index <= 0) return
        after = actionManager.getId(children[index - 1])
        left.remove(vcs, actionManager)
        left.add(vcs, Constraints.FIRST, actionManager)
    }

    /** Puts the group back where it was. One this object never moved is left alone. */
    private fun putBack() {
        val previous = after ?: return
        val actionManager = ActionManager.getInstance()
        val left = left(actionManager) ?: return
        val vcs = actionManager.getAction(VcsToolbarGroup.ID) ?: return
        after = null
        if (left.getChildren(actionManager).firstOrNull() !== vcs) return
        left.remove(vcs, actionManager)
        left.add(vcs, Constraints(Anchor.AFTER, previous), actionManager)
    }

    private fun left(actionManager: ActionManager): DefaultActionGroup? {
        val group = actionManager.getAction(LEFT_ID)
        if (group !is DefaultActionGroup) {
            LOG.warn("$LEFT_ID is ${group?.javaClass?.name ?: "missing"}, not a DefaultActionGroup; the branch stays where it is")
            return null
        }
        return group
    }
}

/**
 * Puts the branch where the settings say before the first frame is built, so the toolbar does not change under the
 * user's eyes, and back to the stock order before Agenstorm is unloaded — nothing else would undo a move made at
 * runtime. Declared under `applicationListeners` in `agenstorm-git.xml`.
 */
class BranchToolbarListener : AppLifecycleListener, DynamicPluginListener {

    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        BranchToolbarPlacement.sync()
    }

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (AgenstormPlugin.isOurs(pluginDescriptor)) BranchToolbarPlacement.sync(false)
    }

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (AgenstormPlugin.isOurs(pluginDescriptor)) BranchToolbarPlacement.sync()
    }
}
