package com.pronskiy.agenstorm.terminal.agents

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.ColoredTreeCellRenderer
import com.pronskiy.agenstorm.terminal.agents.AgentsSidebarState.Grouping
import com.pronskiy.agenstorm.terminal.agents.StatusGrouping.Section

/** Step X6.4: the sidebar grouped by status — section headers, rows that move between them, folds shared by every window. */
class AgentsPanelStatusTest : BasePlatformTestCase() {

    private lateinit var parent: Disposable
    private lateinit var panel: AgentsPanel
    private val clicks = mutableListOf<Pair<ClickPlan, String>>()
    private val state get() = AgentsSidebarState.getInstance()

    private fun row(id: String, status: String, base: String = "/w/app") =
        SessionRow(LiveSession(pid = 1, sessionId = id, cwd = base, name = id, status = status, tmuxSession = null, updatedAt = null, entrypoint = "cli"), SessionPlace.Elsewhere, finished = false)

    private fun groups(app: List<SessionRow>, other: List<SessionRow> = emptyList(), history: List<PastSession> = emptyList()) =
        listOf(ProjectGroup("app", "/w/app", app, history), ProjectGroup("other", "/w/other", other))

    override fun setUp() {
        super.setUp()
        state.loadState(AgentsSidebarState.Layout(groupBy = Grouping.STATUS))
        parent = Disposer.newDisposable()
        panel = AgentsPanel(project, parent, isFront = { true })
        panel.onClick = { plan, id -> clicks += plan to id }
    }

    override fun tearDown() {
        try {
            Disposer.dispose(parent)
            state.loadState(AgentsSidebarState.Layout())
        } finally {
            super.tearDown()
        }
    }

    private fun labels(): List<String> = panel.shownRows().map { item ->
        when (item) {
            is StatusGrouping.StatusGroup -> item.section.name
            is StatusGrouping.Entry.Live -> item.row.session.sessionId
            is StatusGrouping.Entry.Past -> item.past.sessionId
            else -> "?$item"
        }
    }

    private fun text(row: Int): String {
        val renderer = panel.tree.cellRenderer as ColoredTreeCellRenderer
        renderer.getTreeCellRendererComponent(panel.tree, panel.tree.getPathForRow(row).lastPathComponent, false, false, true, row, false)
        return renderer.getCharSequence(false).toString()
    }

    fun testSectionsHoldTheSessionsOfEveryProject() {
        panel.render(groups(listOf(row("a", "busy"), row("w", "waiting")), other = listOf(row("o", "busy", base = "/w/other")), history = listOf(PastSession("p", "/w/app", "old", 0))))

        assertEquals(listOf("NEEDS_INPUT", "w", "RUNNING", "a", "o", "PAST", "p"), labels())
        assertTrue("a header counts its rows", text(2).startsWith("Running") && text(2).contains("2"))
        assertTrue("a row names its project", text(4).contains("other"))
    }

    fun testActiveLeavesThePastSectionOut() {
        state.update { it.apply { show = AgentsSidebarState.Shown.ACTIVE } }

        panel.render(groups(listOf(row("a", "idle")), history = listOf(PastSession("p", "/w/app", "old", 0))))

        assertEquals(listOf("IDLE", "a"), labels())
    }

    fun testARowMovesSectionAndKeepsTheSelection() {
        panel.render(groups(listOf(row("a", "busy"), row("b", "busy"))))
        panel.tree.setSelectionRow(2)

        panel.render(groups(listOf(row("a", "busy"), row("b", "waiting"))))

        assertEquals(listOf("NEEDS_INPUT", "b", "RUNNING", "a"), labels())
        assertEquals("session:b", panel.tree.selectionPath?.let(AgentsPanel::idOf))
    }

    fun testAClickOnARowGoesOutWithItsOwnProject() {
        panel.render(groups(emptyList(), other = listOf(row("o", "busy", base = "/w/other")), history = listOf(PastSession("p", "/w/app", "old", 0))))

        panel.click(panel.tree.getPathForRow(1))
        panel.click(panel.tree.getPathForRow(3))

        assertEquals(listOf(ClickPlan.Nothing to "o", ClickPlan.Resume("/w/app", "/w/app", "p", "old") to "p"), clicks)
    }

    fun testAClickOnAHeaderFoldsItInEveryWindow() {
        panel.render(groups(listOf(row("a", "busy"), row("w", "waiting"))))

        panel.click(panel.tree.getPathForRow(2))

        assertEquals(listOf("NEEDS_INPUT", "w", "RUNNING"), labels())
        assertTrue(StatusGrouping.foldKey(Section.RUNNING) in state.layout.value.collapsed)
        assertTrue(clicks.isEmpty())

        panel.click(panel.tree.getPathForRow(2))
        assertEquals(listOf("NEEDS_INPUT", "w", "RUNNING", "a"), labels())
    }

    fun testBackToProjectsTheSelectionStays() {
        panel.render(groups(listOf(row("a", "busy"), row("b", "idle"))))
        panel.tree.setSelectionRow(3)

        state.update { it.apply { groupBy = Grouping.PROJECT } }
        panel.restore(state.layout.value)

        assertEquals("session:b", panel.tree.selectionPath?.let(AgentsPanel::idOf))
        assertTrue(panel.shownRows().first() is ProjectGroup)
    }

    fun testCopySessionIdWorksOnAStatusRow() {
        panel.render(groups(listOf(row("a", "busy"))))
        panel.tree.setSelectionRow(1)

        val context = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink -> panel.uiDataSnapshot(sink) }

        assertEquals("a", context.getData(AgentsPanel.SESSION_ID))
    }
}
