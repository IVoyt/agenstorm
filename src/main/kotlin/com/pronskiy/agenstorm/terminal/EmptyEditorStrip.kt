package com.pronskiy.agenstorm.terminal

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ThreeComponentsSplitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.JBUI
import com.pronskiy.agenstorm.core.AgenstormSettings
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * The editor's area above a maximized terminal while no file is open. `setMaximized` leaves that area at the pane
 * splitter's minimum, the `ide.mainSplitter.min.size` registry value (30 px), and with no file open the area holds
 * the empty-editor state — in 2026.3 Air's inline agent prompt, whose rows of pickers were drawn over each other in
 * the strip (Roman, 2026-10-19). So while the area is that short and no file is open, what the editors' component
 * holds — the empty-state overlay its controller mounts, and the editors' root with nothing in it — is hidden, and
 * the strip stays empty. An overlay mounted meanwhile is hidden as it arrives.
 *
 * Whether it is hidden is decided again on every resize of the area — un-maximizing grows it, whoever does it, the
 * platform's own maximize toggle included, and the platform fires nothing else when it does — and whenever a file
 * opens or closes. A file about to open shows the editors first, so it opens into a component on screen and can take
 * the keyboard; one an agent opens in the background while the terminal stays maximized also gets its tab row
 * uncovered, as maximizing does (J1.8). The editors' component itself stays visible: it is the splitter's middle
 * component, which would give a hidden middle's height to the terminal, and then nothing would resize when the
 * terminal is restored.
 *
 * It listens from the first maximize on, until the project closes or the feature goes off.
 */
@Service(Service.Level.PROJECT)
class EmptyEditorStrip(private val project: Project) : Disposable {

    private var listening: Disposable? = null
    private var area: JComponent? = null
    private var hidden: List<Component> = emptyList()

    /** EDT, from [TerminalMaximizeToggleAction.afterMaximized]: the terminal was just maximized. */
    fun watch(terminal: ToolWindow) {
        val area = editorAreaAbove(terminal) ?: return
        if (listening == null || this.area !== area) listen(area)
        update()
    }

    private fun listen(area: JComponent) {
        stop()
        val lifetime = Disposer.newDisposable(this, "Agenstorm empty editor strip")
        val resized = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = update()
        }
        area.addComponentListener(resized)
        Disposer.register(lifetime, Disposable { area.removeComponentListener(resized) })
        editorsIn(area)?.let { editors ->
            val mounted = object : ContainerAdapter() {
                override fun componentAdded(e: ContainerEvent) = update()
            }
            editors.addContainerListener(mounted)
            Disposer.register(lifetime, Disposable { editors.removeContainerListener(mounted) })
        }
        val connection = project.messageBus.connect(lifetime)
        connection.subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, object : FileEditorManagerListener.Before {
            override fun beforeFileOpened(source: FileEditorManager, file: VirtualFile) = show()
        })
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                update()
                uncoverTabsLater()
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) = update()
        })
        this.area = area
        listening = lifetime
    }

    private fun update() {
        val area = area ?: return
        if (project.isDisposed) return
        if (!AgenstormSettings.getInstance().state.terminalMaximizeEnabled) return stop()
        val editors = editorsIn(area) ?: return show()
        val noFiles = FileEditorManager.getInstance(project).openFiles.isEmpty()
        if (shouldHide(noFiles, area.height, JBUI.scale(MAX_STRIP_HEIGHT))) hide(editors) else show()
    }

    private fun hide(editors: JComponent) {
        val shown = editors.components.filter { it.isVisible }
        if (shown.isEmpty()) return
        shown.forEach { it.isVisible = false }
        hidden = hidden + shown
        editors.revalidate()
        editors.repaint()
    }

    private fun show() {
        if (hidden.isEmpty()) return
        val parents = hidden.mapNotNull { it.parent }.toSet()
        hidden.forEach { it.isVisible = true }
        hidden = emptyList()
        parents.forEach { it.revalidate(); it.repaint() }
    }

    private fun stop() {
        show()
        listening?.let(Disposer::dispose)
        listening = null
        area = null
    }

    /** The tab row of a file opened while the terminal stays maximized shows whole, as on maximizing (J1.8). */
    private fun uncoverTabsLater() {
        ApplicationManager.getApplication().invokeLater({
            val terminal = TerminalMaximizeToggleAction.terminalOf(project) ?: return@invokeLater
            if (TerminalMaximizeToggleAction.stateOf(project, terminal).isTerminalMaximized) EditorTabRow.uncover(project, terminal)
        }, ModalityState.nonModal(), project.disposed)
    }

    /** The editors' component: [area] itself, or inside it. */
    private fun editorsIn(area: JComponent): JComponent? {
        val editors = (FileEditorManager.getInstance(project) as? FileEditorManagerEx)?.component ?: return null
        return editors.takeIf { SwingUtilities.isDescendingFrom(it, area) }
    }

    override fun dispose() {
        show()
    }

    companion object {
        /** Taller than this, the area has room for the empty-editor state; the splitter's minimum is 30. */
        const val MAX_STRIP_HEIGHT = 64

        /** Pure: the editors go while no file is open and the area is a strip; a height of 0 is not laid out yet. */
        fun shouldHide(noFiles: Boolean, areaHeight: Int, maxStripHeight: Int): Boolean =
            noFiles && areaHeight in 1..maxStripHeight

        /** The middle of the vertical splitter [terminal] sits in — the editor's area above it — or null. */
        fun editorAreaAbove(terminal: ToolWindow): JComponent? {
            val content = terminal.component
            val splitter = ComponentUtil.getParentOfType(ThreeComponentsSplitter::class.java, content) ?: return null
            if (!splitter.orientation) return null
            val inner = splitter.innerComponent ?: return null
            return inner.takeUnless { SwingUtilities.isDescendingFrom(content, it) }
        }
    }
}
