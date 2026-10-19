package com.pronskiy.agenstorm.tabs

import com.intellij.ide.ReopenProjectAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import com.pronskiy.agenstorm.tabs.ui.RecentProjectRenderer
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.ListCellRenderer

/** The "+" popup's recent projects: icon, name, path and the branch as it is known now (Roman, 2026-10-19). */
class RecentProjectRendererTest : BasePlatformTestCase() {

    fun testAProjectShowsItsIconNameAndPathAndTheBranchItIsGiven() {
        val action = ReopenProjectAction("/fake/agenstorm", "agenstorm", "agenstorm")
        val labels = labelsOf(RecentProjectRenderer.pane(action, "main", isSelected = false))
        assertEquals(listOf("agenstorm", action.projectPathToDisplay, "main"), labels.mapNotNull { it.text?.takeIf(String::isNotEmpty) })
        assertNotNull("the project's icon", labels.first { it.text.isNullOrEmpty() }.icon)
        assertNotNull("the branch has its icon", labels.single { it.text == "main" }.icon)
    }

    fun testWithNoBranchThereIsNoBranchLine() {
        val action = ReopenProjectAction("/fake/agenstorm", "agenstorm", "agenstorm")
        val texts = labelsOf(RecentProjectRenderer.pane(action, null, isSelected = false)).mapNotNull { it.text?.takeIf(String::isNotEmpty) }
        assertEquals(listOf("agenstorm", action.projectPathToDisplay), texts)
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
