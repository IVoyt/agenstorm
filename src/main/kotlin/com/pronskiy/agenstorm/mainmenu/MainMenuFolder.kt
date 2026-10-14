package com.pronskiy.agenstorm.mainmenu

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.ui.customization.CustomActionsListener
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Anchor
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.pronskiy.agenstorm.core.AgenstormPlugin
import com.pronskiy.agenstorm.core.AgenstormSettings

/**
 * Step Y1.4. Navigate, Code, Refactor, Run, Tools and Git leave the main menu's top level for one *More* menu.
 * Roman: "move main menu items under one (navigate, code, refactor, run, tools, git)".
 *
 * The stock groups are moved inside the stock `MainMenu` with `DefaultActionGroup.remove`/`add`, not replaced
 * (decision 109). Every id keeps its stock instance, so a plugin installed later that adds to `ToolsMenu` or to
 * `MainMenu` adds where it always did; an `ActionSlot` on the six ids would have left placeholders for it to add to.
 * The menu bar, the macOS screen menu and the New UI's main menu button all re-read `MainMenu` on the action timer,
 * so a fold or an unfold shows without a restart.
 *
 * EDT only: the main menu is shared by every window.
 */
object MainMenuFolder {

    private val LOG = logger<MainMenuFolder>()

    const val MAIN_MENU_ID = "MainMenu"
    const val MORE_ID = "Agenstorm.MoreMenu"

    /** The top level's ids as they were before the fold, so an unfold puts each entry back where it was. */
    private var snapshot: List<String?>? = null

    /** The entries this fold took out. */
    private var folded: List<String> = emptyList()

    /** Follows the setting. Idempotent, so every entry point can just call it. */
    fun sync() = sync(AgenstormSettings.getInstance().state.mainMenuMoreEnabled)

    @Synchronized
    fun sync(enabled: Boolean) {
        if (enabled) fold() else unfold()
    }

    @Synchronized
    fun fold() {
        val actionManager = ActionManager.getInstance()
        val menu = mainMenu(actionManager) ?: return
        val more = actionManager.getAction(MORE_ID) ?: return
        val children = menu.getChildren(actionManager)
        if (children.any { it === more }) return
        val ids = children.map { actionManager.getId(it) }
        val plan = MoreMenuPlan.fold(ids) ?: return
        for (child in children) {
            if (actionManager.getId(child) in plan.remove) menu.remove(child, actionManager)
        }
        menu.add(more, plan.moreAfter?.let { Constraints(Anchor.AFTER, it) } ?: Constraints.FIRST, actionManager)
        snapshot = ids
        folded = plan.remove
        menusChanged()
    }

    /** Puts the entries back where they were. A menu this object never folded is left alone. */
    @Synchronized
    fun unfold() {
        val actionManager = ActionManager.getInstance()
        val menu = mainMenu(actionManager) ?: return
        val more = actionManager.getAction(MORE_ID) ?: return
        if (menu.getChildren(actionManager).none { it === more }) return
        menu.remove(more, actionManager)
        val present = menu.getChildren(actionManager).mapNotNull { actionManager.getId(it) }.toSet()
        val owed = folded.ifEmpty { MoreMenuPlan.FOLDED_IDS }.filter { it !in present }
        for ((id, after) in MoreMenuPlan.unfold(snapshot ?: MoreMenuPlan.DEFAULT_ORDER, owed, present)) {
            val action = actionManager.getAction(id) ?: continue
            menu.add(action, after?.let { Constraints(Anchor.AFTER, it) } ?: Constraints.FIRST, actionManager)
        }
        snapshot = null
        folded = emptyList()
        menusChanged()
    }

    /** True while *More* is on the main menu. */
    fun isFolded(): Boolean {
        val actionManager = ActionManager.getInstance()
        val more = actionManager.getAction(MORE_ID) ?: return false
        return mainMenu(actionManager)?.getChildren(actionManager)?.any { it === more } == true
    }

    private fun mainMenu(actionManager: ActionManager): DefaultActionGroup? {
        val menu = actionManager.getAction(MAIN_MENU_ID)
        if (menu !is DefaultActionGroup) {
            LOG.warn("$MAIN_MENU_ID is ${menu?.javaClass?.name ?: "missing"}, not a DefaultActionGroup; the main menu stays as it is")
            return null
        }
        return menu
    }

    /** Toolbars and customised groups re-read on the next update; the menu bar follows the action timer anyway. */
    private fun menusChanged() {
        CustomActionsListener.fireSchemaChanged()
    }
}

/**
 * Step Y1.4: *More*, holding the six folded menus as submenus. It resolves their ids at each update rather than holding
 * instances, so the VCS menu's own name ("Git") and children, and any group another plugin put in one of those slots,
 * come through as they are. Registered in `plugin.xml` with no `add-to-group`: only [MainMenuFolder] places it.
 */
class MoreMenuGroup : ActionGroup(), DumbAware {

    init {
        templatePresentation.isPopupGroup = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val actionManager = ActionManager.getInstance()
        return MoreMenuPlan.FOLDED_IDS.mapNotNull { actionManager.getAction(it) }.toTypedArray()
    }
}

/** Folds the menu before the first frame is built (declared under `applicationListeners`). */
class MainMenuLifecycleListener : AppLifecycleListener {

    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        MainMenuFolder.sync()
    }
}

/**
 * Unfolds before Agenstorm is unloaded — the move happens at runtime, so nothing else would undo it and the six menus
 * would be gone until a restart — and folds again after a dynamic update loads the new version.
 */
class MainMenuUnloadListener : DynamicPluginListener {

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (AgenstormPlugin.isOurs(pluginDescriptor)) MainMenuFolder.unfold()
    }

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (AgenstormPlugin.isOurs(pluginDescriptor)) MainMenuFolder.sync()
    }
}
