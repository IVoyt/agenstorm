package com.pronskiy.agenstorm.terminal.agents

import com.pronskiy.agenstorm.terminal.agents.StatusGrouping.Section
import org.junit.Assert.assertEquals
import org.junit.Test

/** Step X6.4: every open project's sessions under their status — needs input, finished, running, idle, past. */
class StatusGroupingTest {

    private fun row(id: String, status: String?, finished: Boolean = false, started: Long? = null, base: String = "/w/alpha") =
        SessionRow(LiveSession(pid = 1, sessionId = id, cwd = base, name = id, status = status, tmuxSession = null, updatedAt = null, entrypoint = null, startedAt = started), SessionPlace.Elsewhere, finished)

    private fun past(id: String, at: Long) = PastSession(id, "/w", "title $id", lastActivity = at)

    private val alpha = ProjectGroup("alpha", "/w/alpha")
    private val beta = ProjectGroup("beta", "/w/beta")

    private fun ids(sections: List<StatusGrouping.StatusGroup>) = sections.associate { group ->
        group.section to group.entries.map { entry ->
            when (entry) {
                is StatusGrouping.Entry.Live -> entry.row.session.sessionId
                is StatusGrouping.Entry.Past -> entry.past.sessionId
            }
        }
    }

    @Test
    fun eachSessionGoesUnderItsStatusInOrder() {
        val sections = StatusGrouping.sections(
            listOf(alpha.copy(sessions = listOf(row("busy", "busy"), row("wait", "waiting"), row("done", "idle", finished = true), row("idle", "idle")))),
            includePast = true,
        )

        assertEquals(listOf(Section.NEEDS_INPUT, Section.FINISHED, Section.RUNNING, Section.IDLE), sections.map { it.section })
        assertEquals(mapOf(Section.NEEDS_INPUT to listOf("wait"), Section.FINISHED to listOf("done"), Section.RUNNING to listOf("busy"), Section.IDLE to listOf("idle")), ids(sections))
    }

    @Test
    fun shellModeNoStatusAndAnUnknownOneAreIdle() {
        val sections = StatusGrouping.sections(listOf(alpha.copy(sessions = listOf(row("a", "shell"), row("b", null), row("c", "compacting")))), includePast = true)

        assertEquals(mapOf(Section.IDLE to listOf("a", "b", "c")), ids(sections))
    }

    @Test
    fun emptySectionsAreLeftOut() {
        assertEquals(emptyList<StatusGrouping.StatusGroup>(), StatusGrouping.sections(listOf(alpha, beta), includePast = true))
    }

    @Test
    fun sessionsOfEveryProjectMixByStartAndKeepTheirProject() {
        val sections = StatusGrouping.sections(
            listOf(alpha.copy(sessions = listOf(row("a2", "busy", started = 20))), beta.copy(sessions = listOf(row("b1", "busy", started = 10, base = "/w/beta"), row("b3", "busy", started = null, base = "/w/beta")))),
            includePast = true,
        )

        val running = sections.single().entries.map { it as StatusGrouping.Entry.Live }
        assertEquals("by start, an unknown start last", listOf("b1", "a2", "b3"), running.map { it.row.session.sessionId })
        assertEquals(listOf("/w/beta", "/w/alpha", "/w/beta"), running.map { it.basePath })
        assertEquals(listOf("beta", "alpha", "beta"), running.map { it.projectName })
    }

    @Test
    fun pastSessionsComeLastNewestFirstAndOnlyWhenShown() {
        val groups = listOf(alpha.copy(history = listOf(past("p-old", 1))), beta.copy(history = listOf(past("p-new", 5), past("p-mid", 3))))

        val shown = StatusGrouping.sections(groups + listOf(ProjectGroup("gamma", "/w/gamma", listOf(row("g", "idle")))), includePast = true)

        assertEquals(listOf(Section.IDLE, Section.PAST), shown.map { it.section })
        assertEquals(listOf("p-new", "p-mid", "p-old"), ids(shown).getValue(Section.PAST))
        assertEquals("/w/alpha", (shown.last().entries.last() as StatusGrouping.Entry.Past).basePath)
        assertEquals(emptyList<StatusGrouping.StatusGroup>(), StatusGrouping.sections(groups, includePast = false))
    }

    @Test
    fun aSectionsFoldKeyIsItsName() {
        assertEquals("status:NEEDS_INPUT", StatusGrouping.foldKey(Section.NEEDS_INPUT))
    }
}
