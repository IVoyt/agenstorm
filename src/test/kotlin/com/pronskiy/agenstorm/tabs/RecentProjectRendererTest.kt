package com.pronskiy.agenstorm.tabs

import com.intellij.ide.ReopenProjectAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import com.pronskiy.agenstorm.tabs.ui.RecentProjectRenderer
import java.awt.Component
import java.awt.Container
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.ListCellRenderer

/** The "+" popup's recent projects: one line of icon, name and the branch as it is known now (Roman, 2026-10-19). */
class RecentProjectRendererTest : BasePlatformTestCase() {

    fun testAProjectIsOneLineOfIconNameAndTheBranchItIsGiven() {
        val action = ReopenProjectAction("/fake/agenstorm", "agenstorm", "agenstorm")
        val pane = RecentProjectRenderer.pane(action, "main", isSelected = false)
        pane.size = pane.preferredSize
        UIUtil.uiTraverser(pane).filter(Container::class.java).forEach(Container::doLayout)
        val labels = labelsOf(pane)
        assertEquals("no path, nothing but the name and the branch", listOf("agenstorm", "main"), labels.map { it.text })
        assertNotNull("the project's icon", labels.first().icon)
        assertNotNull("the branch has its icon", labels.last().icon)
        assertTrue("side by side", labels.last().x > labels.first().x + labels.first().width - 1)
        assertTrue("one line", labels.all { it.height > 0 && it.y < labels.first().height })
    }

    fun testWithNoBranchThereIsOnlyTheName() {
        val action = ReopenProjectAction("/fake/agenstorm", "agenstorm", "agenstorm")
        assertEquals(listOf("agenstorm"), labelsOf(RecentProjectRenderer.pane(action, null, isSelected = false)).map { it.text })
    }

    fun testAnythingButARecentProjectIsLeftToThePopupsOwnRenderer() {
        val base = DefaultListCellRenderer()
        @Suppress("UNCHECKED_CAST")
        val renderer = RecentProjectRenderer(base as ListCellRenderer<Any?>) { error("not a project") }
        val component = renderer.getListCellRendererComponent(JList<Any?>(), "Open…", 0, false, false)
        assertSame(base, component)
    }

    fun testBranchesAreReadFromTheRecentProjectActions() {
        val actions = listOf(
            ReopenProjectAction("/fake/a", "a", "a", "main"),
            ReopenProjectAction("/fake/b", "b", "b"),
        )
        assertEquals(mapOf("/fake/a" to "main", "/fake/b" to null), ProjectTabActions.branchesOf(actions))
    }

    private fun labelsOf(component: Component): List<JBLabel> =
        UIUtil.uiTraverser(component).filter(JBLabel::class.java).toList()
}
