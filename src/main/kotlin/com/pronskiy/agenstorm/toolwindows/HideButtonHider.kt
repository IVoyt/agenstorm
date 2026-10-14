package com.pronskiy.agenstorm.toolwindows

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType
import com.pronskiy.agenstorm.core.AgenstormPlugin
import com.pronskiy.agenstorm.core.AgenstormSettings
import com.pronskiy.agenstorm.core.AgenstormSettingsListener
import java.awt.Component
import java.awt.Container
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Step Y1.3. The Terminal header's top-right corner shows only ⋯: the platform's Hide button beside it is hidden.
 * Roman: "remove minimize icon from terminal top right corner, keep only three dots" — the Terminal only.
 *
 * The header (`com.intellij.toolWindow.ToolWindowHeader`, `@ApiStatus.Internal`) builds its toolbar from the tab list,
 * the title actions, Dock, ⋯ and its own `HideAction`, whose `update` is only `setEnabledAndVisible(true)`. No public
 * API, registry key or advanced setting takes the button out, so the decorator, the header and the action are matched
 * by class name, as `statusbar/StatusTextHider` matches the status text: no platform class is referenced, and a
 * header laid out differently finds nothing and keeps its button (decision 110).
 *
 * The button is an `ActionButton`, which implements the public [AnActionHolder]; neither it nor its toolbar ever
 * calls `setVisible`, and the toolbar gives an invisible child no size. A toolbar does rebuild its buttons when its
 * visible actions change, so every container in the header is watched and a Hide button added to it is hidden too.
 */
class HideButtonHider(private val names: Names = Names.PLATFORM) {

    /** The classes matched by name. Tests pass stand-ins. */
    data class Names(val decorator: String, val header: String, val hideAction: String) {
        companion object {
            val PLATFORM = Names(DECORATOR_CLASS, HEADER_CLASS, HIDE_ACTION_CLASS)
        }
    }

    /** Containers in a header we listen to; weak, so a decorator the platform rebuilt takes its listeners with it. */
    private val watched = WeakHashMap<Container, ContainerListener>()

    /**
     * EDT. Hides ([hide]) or shows the Hide buttons in the header of the tool window whose content is [content], and
     * starts or stops watching that header. Returns whether a header was found.
     */
    fun sync(content: Component, hide: Boolean): Boolean {
        val decorator = outermostDecorator(content) ?: return false
        val headers = mutableListOf<Container>()
        walk(decorator, skip = content) { if (it is Container && isA(it, names.header)) headers += it }
        for (header in headers) {
            walk(header) { component ->
                if (component is Container) {
                    if (hide) watch(component) else unwatch(component)
                }
                if (isHideButton(component)) setShown(component, !hide)
            }
        }
        return headers.isNotEmpty()
    }

    /** True while any container is watched; for tests and the debug log. */
    fun isWatching(): Boolean = watched.isNotEmpty()

    /** The outermost decorator above [content]: a split tool window nests decorators, the header belongs to the outer one. */
    private fun outermostDecorator(content: Component): Container? {
        var found: Container? = null
        var current: Container? = content.parent
        while (current != null) {
            if (current.javaClass.name == names.decorator) found = current
            current = current.parent
        }
        return found
    }

    private fun isHideButton(component: Component): Boolean =
        component is AnActionHolder && component.action?.javaClass?.name == names.hideAction

    private fun watch(container: Container) {
        if (watched.containsKey(container)) return
        val listener = object : ContainerListener {
            override fun componentAdded(e: ContainerEvent) {
                walk(e.child) { component ->
                    if (component is Container) watch(component)
                    if (isHideButton(component)) setShown(component, false)
                }
            }

            override fun componentRemoved(e: ContainerEvent) = Unit
        }
        watched[container] = listener
        container.addContainerListener(listener)
    }

    private fun unwatch(container: Container) {
        watched.remove(container)?.let(container::removeContainerListener)
    }

    private fun setShown(component: Component, show: Boolean) {
        if (component.isVisible == show) return
        component.isVisible = show
        component.parent?.let {
            it.revalidate()
            it.repaint()
        }
    }

    companion object {
        const val DECORATOR_CLASS = "com.intellij.toolWindow.InternalDecoratorImpl"

        /** Abstract: the header is a subclass, so it is matched anywhere in the class chain. */
        const val HEADER_CLASS = "com.intellij.toolWindow.ToolWindowHeader"
        const val HIDE_ACTION_CLASS = "com.intellij.toolWindow.ToolWindowHeader\$HideAction"

        /** True when [component]'s class or one of its superclasses is named [className]. */
        fun isA(component: Component, className: String): Boolean =
            generateSequence<Class<*>>(component.javaClass) { it.superclass }.any { it.name == className }

        /** Visits [root] and everything under it, except the subtree of [skip]. */
        fun walk(root: Component, skip: Component? = null, visit: (Component) -> Unit) {
            if (root === skip) return
            visit(root)
            if (root is Container) for (child in root.components) walk(child, skip, visit)
        }
    }
}

/**
 * Step Y1.3. Applies [HideButtonHider] to this project's Terminal tool window, following the setting. Only once the
 * Terminal has made its content (`contentManagerIfCreated`): asking for its component earlier would create it, and
 * with it start the platform's tab restore.
 */
@Service(Service.Level.PROJECT)
class TerminalHideButton(private val project: Project) : Disposable {

    private val hider = HideButtonHider()

    /** Collapses the passes asked for by a burst of tool window events into one. */
    private val scheduled = AtomicBoolean(false)

    /** [sync] with the setting, on the EDT, at most one pass pending, also while the settings dialog is open. */
    fun applyLater() {
        if (!scheduled.compareAndSet(false, true)) return
        ApplicationManager.getApplication().invokeLater({
            scheduled.set(false)
            if (!project.isDisposed) sync(AgenstormSettings.getInstance().state.terminalHideHeaderHideButton)
        }, ModalityState.any())
    }

    /** EDT. The button back, whatever the setting says; plugin unload. */
    fun restore() = sync(hide = false)

    private fun sync(hide: Boolean) {
        if (project.isDisposed) return
        val terminal = ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_ID) ?: return
        val content = terminal.contentManagerIfCreated?.component ?: return
        val found = hider.sync(content, hide)
        if (!found && LOG.isDebugEnabled) LOG.debug("Terminal header: no ${HideButtonHider.HEADER_CLASS} under its decorator")
    }

    override fun dispose() = Unit

    companion object {
        private val LOG = logger<TerminalHideButton>()
        const val TERMINAL_ID = "Terminal"

        fun getInstance(project: Project): TerminalHideButton = project.service()
    }
}

/**
 * The Terminal's decorator and header are made when it is first shown and remade when it floats, docks or moves, so
 * every Terminal event re-applies; the passes collapse into one and only walk the header.
 */
class TerminalHideButtonToolWindowListener(private val project: Project) : ToolWindowManagerListener {

    override fun toolWindowShown(toolWindow: ToolWindow) {
        if (toolWindow.id == TerminalHideButton.TERMINAL_ID) TerminalHideButton.getInstance(project).applyLater()
    }

    override fun stateChanged(toolWindowManager: ToolWindowManager, toolWindow: ToolWindow, changeType: ToolWindowManagerEventType) {
        if (toolWindow.id == TerminalHideButton.TERMINAL_ID) TerminalHideButton.getInstance(project).applyLater()
    }
}

/** Registered in `plugin.xml`: follows the setting from project open, on Terminal events and when the settings page applies. */
class TerminalHideButtonStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val service = TerminalHideButton.getInstance(project)
        ApplicationManager.getApplication().messageBus.connect(service)
            .subscribe(AgenstormSettingsListener.TOPIC, AgenstormSettingsListener { service.applyLater() })
        project.messageBus.connect(service)
            .subscribe(ToolWindowManagerListener.TOPIC, TerminalHideButtonToolWindowListener(project))
        service.applyLater()
    }
}

/** Registered in `plugin.xml`: the Hide button comes back before the plugin's classes go. */
class TerminalHideButtonUnloadListener : DynamicPluginListener {

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (!AgenstormPlugin.isOurs(pluginDescriptor)) return
        for (project in ProjectManager.getInstance().openProjects) {
            if (!project.isDisposed) TerminalHideButton.getInstance(project).restore()
        }
    }
}
