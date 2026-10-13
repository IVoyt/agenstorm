package com.pronskiy.agenstorm.terminal.agents

/**
 * Step X6.4. Pure: the sidebar grouped by status instead of by project (decision 107). Every open project's sessions go
 * under one of five sections, in this order, and a section with nothing in it is left out: *needs input* (`waiting`),
 * *finished* (a dot, X6.1), *running* (`busy`), *idle* (`idle`, `shell`, no status or one Claude may add later), and
 * *past*, under *All Sessions* only. Each entry keeps the project it belongs to, for its hint and its click.
 */
object StatusGrouping {

    enum class Section { NEEDS_INPUT, FINISHED, RUNNING, IDLE, PAST }

    /** One row under a section: a running or a past session, and its project. */
    sealed interface Entry {
        val basePath: String
        val projectName: String

        data class Live(val row: SessionRow, override val basePath: String, override val projectName: String) : Entry

        data class Past(val past: PastSession, override val basePath: String, override val projectName: String) : Entry
    }

    /** A section and its rows; the header shows [entries]' count. */
    data class StatusGroup(val section: Section, val entries: List<Entry>)

    /**
     * [groups] (the model's, by project) → their sections. Running sessions are ordered by start across projects, so a
     * row keeps its place while its name and status change; past ones newest first. [includePast] is *All Sessions*.
     */
    fun sections(groups: List<ProjectGroup>, includePast: Boolean): List<StatusGroup> {
        val live = groups.flatMap { group -> group.sessions.map { Entry.Live(it, group.basePath, group.name) } }
            .sortedWith(compareBy<Entry.Live>({ it.row.session.startedAt ?: Long.MAX_VALUE }, { it.row.session.sessionId }))
            .groupBy { sectionOf(it.row) }
        val past = if (includePast) {
            groups.flatMap { group -> group.history.map { Entry.Past(it, group.basePath, group.name) } }.sortedByDescending { it.past.lastActivity }
        } else {
            emptyList()
        }
        return Section.entries.mapNotNull { section ->
            val entries = if (section == Section.PAST) past else live[section].orEmpty()
            entries.takeIf { it.isNotEmpty() }?.let { StatusGroup(section, it) }
        }
    }

    /** Where a running session goes; a finished one keeps its dot until seen, so *finished* beats *idle*. */
    fun sectionOf(row: SessionRow): Section = when {
        row.session.status == WAITING -> Section.NEEDS_INPUT
        row.finished -> Section.FINISHED
        row.session.status == BUSY -> Section.RUNNING
        else -> Section.IDLE
    }

    /** A folded section's entry in the shared layout's `collapsed`, beside the folded projects' base paths. */
    fun foldKey(section: Section): String = "status:${section.name}"

    private const val BUSY = "busy"
    private const val WAITING = "waiting"
}
