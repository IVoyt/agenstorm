package com.pronskiy.agenstorm.terminal.agents

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.pronskiy.agenstorm.core.AgenstormSettings
import com.pronskiy.agenstorm.core.AgenstormSettingsListener
import com.pronskiy.agenstorm.core.ProjectAttention
import com.pronskiy.agenstorm.terminal.tmux.BackgroundTerminals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Steps X1.2, X2.4, X3.2, X6.1, X6.2 and X6.5. The one model every window's Agents sidebar draws (decision 102): a
 * group per open project, in the same order everywhere, with the Claude Code sessions running in it, where each can be
 * shown and
 * whether it finished while its tab was not on screen ([AttentionTracker]), and its newest past sessions
 * ([AgenstormSettings.State.agentSessionsHistory] of them). After each read it tells what happened off screen
 * ([notifier]) and which projects have an agent waiting ([ProjectAttention], for the project tabs). While the setting
 * is on it reads `~/.claude/sessions` every [POLL], and at once when a project opens ([AgentsStartupActivity]) or closes
 * ([AgentsProjectClosed]) and when a Terminal tab is added ([AgentsTabsListener]); with the setting off it reads nothing
 * there. The settings page turns the sidebar on and off in every window through [AgentsToolWindowFactory.syncAvailability].
 */
@Service(Service.Level.APP)
class AgentSessionsModel(private val scope: CoroutineScope) : Disposable {

    private val groupsFlow = MutableStateFlow<List<ProjectGroup>>(emptyList())
    private val passes = Mutex()
    private val attention = AttentionTracker()

    /** Step X6.2. Each read's events about sessions the IDE can bring up, in projects still open; told by [notifier]. */
    private val newsFlow = MutableSharedFlow<List<AttentionEvent>>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    internal var notifier = AgentNotifier(projectName = { base -> groups.value.firstOrNull { it.basePath == base }?.name ?: base }, open = ::open)
    private val transcripts = ClaudeTranscripts()

    /** The past sessions as last read, by base path, and when; read every [HISTORY_POLL] or on [refresh], under [passes]. */
    private var history: Map<String, List<PastSession>> = emptyMap()
    private var historyReadAt = 0L

    /** `~/.claude`; tests point it elsewhere. */
    internal var claudeHome: () -> Path = ClaudeLiveSessions::home

    val groups: StateFlow<List<ProjectGroup>> = groupsFlow.asStateFlow()

    val news: SharedFlow<List<AttentionEvent>> = newsFlow.asSharedFlow()

    init {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(AgenstormSettingsListener.TOPIC, AgenstormSettingsListener { state ->
                AgentsToolWindowFactory.syncAvailability(state.agentSessionsEnabled)
                refresh()
            })
        // Any modality: a notice is most needed while a dialog waits in a window you left; one that fails must not
        // stop the ones after it.
        scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            newsFlow.collect { news ->
                try {
                    notifier.tell(news)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    LOG.warn("Agenstorm: telling about Claude Code sessions failed", e)
                }
            }
        }
        scope.launch {
            while (isActive) {
                if (enabled()) safePass(closing = null, readHistory = false)
                delay(POLL)
            }
        }
    }

    /**
     * The open projects now — leaving out [closing], which may still be listed — each keeping the sessions it had, then a
     * read of the sessions in the background. Any thread.
     */
    fun refresh(closing: Project? = null) {
        groupsFlow.update { current ->
            val before = current.associateBy { it.basePath }
            ProjectGroup.sorted(openGroups(closing).map { group ->
                before[group.basePath]?.let { group.copy(sessions = it.sessions.map { row -> row.withoutTabOf(closing) }, history = it.history) } ?: group
            })
        }
        scope.launch { safePass(closing, readHistory = true) }
    }

    /** The user clicked [sessionId]: its dot goes. */
    fun seen(sessionId: String) {
        attention.seen(sessionId)
        groupsFlow.update { current ->
            current.map { group -> group.copy(sessions = group.sessions.map { if (it.session.sessionId == sessionId) it.copy(finished = false) else it }) }
        }
    }

    /** One pass, whatever it throws: a file Claude deletes mid-read must not stop the polling for the rest of the run. */
    private suspend fun safePass(closing: Project?, readHistory: Boolean) {
        try {
            pass(closing, readHistory)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LOG.warn("Agenstorm: reading Claude Code sessions failed", e)
        }
    }

    private suspend fun pass(closing: Project?, readHistory: Boolean) = passes.withLock {
        val on = enabled()
        // Only UI state is read here, so a modal dialog need not hold the read up (any modality, X6.2).
        val (projects, probes, screen) = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            Triple(openGroups(closing), if (on) AgentTabLocator.probes() else emptyList(), if (on) OnScreen.current() else OnScreen.Screen.nothing())
        }
        if (!on) {
            // The next read after the setting comes back on is a first read: nothing seen meanwhile is news.
            attention.reset()
            ProjectAttention.getInstance().clear()
            groupsFlow.update { ProjectGroup.sorted(projects.filter { stillOpen(it.basePath, closing) }) }
            return@withLock
        }
        val limit = AgenstormSettings.getInstance().state.agentSessionsHistory
        val (rows, running, found) = withContext(Dispatchers.IO) {
            val home = claudeHome()
            val live = ClaudeLiveSessions.read(home)
            val background = BackgroundTerminals.getInstance().sessions.value.mapTo(HashSet()) { it.name }
            val places = AgentTabLocator.locateAll(live, probes, background, ProcessTree.snapshot())
            val roots = projects.flatMap { group -> listOf(group.basePath to group.basePath, realPath(group.basePath) to group.basePath) }.toMap()
            val grouped = SessionGrouping.group(live, roots)
            val events = attention.update(grouped.flatMap { (base, sessions) -> sessions.map { it to base } }, OnScreen.ids(places, screen)).events
            val running = live.mapTo(HashSet()) { it.sessionId }
            val now = System.currentTimeMillis()
            if (readHistory || now - historyReadAt >= HISTORY_POLL.inWholeMilliseconds || projects.any { it.basePath !in history }) {
                history = projects.associate { it.basePath to transcripts.recent(home, transcriptFolderOf(home, it.basePath), limit, running) }
                historyReadAt = now
            }
            val rows = grouped.mapValues { (_, sessions) -> sessions.map { SessionRow(it, places[it.sessionId] ?: SessionPlace.Elsewhere, finished = false) } }
            Triple(rows, running, events to places)
        }
        // Written as one update against what is open now: a project that closed during the read stays out, and a dot
        // cleared meanwhile stays cleared.
        groupsFlow.update {
            val done = attention.current()
            ProjectGroup.sorted(projects.filter { stillOpen(it.basePath, closing) }.map { group ->
                group.copy(
                    sessions = rows[group.basePath].orEmpty().map { row -> row.copy(finished = row.session.sessionId in done).withoutClosedTab() },
                    history = history[group.basePath].orEmpty().filter { it.sessionId !in running },
                )
            })
        }
        ProjectAttention.getInstance().set(if (AgenstormSettings.getInstance().state.agentSessionsTabBadge) waitingByProject(groupsFlow.value) else emptyMap())
        val (events, places) = found
        tell(newsOf(events, places, groupsFlow.value.mapTo(HashSet()) { it.basePath }))
    }

    /** Step X6.2. Hands [news] to the [notifier], on the EDT and in order; nothing for an empty list. Any thread. */
    internal fun tell(news: List<AttentionEvent>) {
        if (news.isNotEmpty()) newsFlow.tryEmit(news)
    }

    /** Step X6.2. Brings up the session [event] is about, as a click on its row would; nothing once it is gone. EDT. */
    private fun open(event: AttentionEvent) {
        val group = groups.value.firstOrNull { it.basePath == event.basePath } ?: return
        val row = group.sessions.firstOrNull { it.session.sessionId == event.session.sessionId } ?: return
        seen(row.session.sessionId)
        AgentSessionOpener.open(ClickPlan.of(row, group.basePath))
    }

    private fun stillOpen(basePath: String, closing: Project?): Boolean =
        ProjectManager.getInstance().openProjects.any { it != closing && !it.isDisposed && it.basePath == basePath }

    /** The folder Claude Code names the transcripts after: the project's real path when its own has no folder (a symlink). */
    private fun transcriptFolderOf(home: Path, basePath: String): String =
        if (Files.isDirectory(ClaudeTranscripts.folderOf(home, basePath))) basePath else realPath(basePath)

    private fun realPath(path: String): String = runCatching { Path.of(path).toRealPath().toString() }.getOrDefault(path)

    private fun openGroups(closing: Project?): List<ProjectGroup> =
        ProjectManager.getInstance().openProjects.filter { it != closing && !it.isDisposed && !it.isDefault }
            .mapNotNull { project -> project.basePath?.let { ProjectGroup(project.name, it) } }

    private fun enabled() = AgenstormSettings.getInstance().state.agentSessionsEnabled

    /** Calls [render] on the EDT with the groups now and after every change, until [parent] is disposed. */
    fun collect(parent: Disposable, render: (List<ProjectGroup>) -> Unit) = follow(groups, parent, render)

    /** Calls [render] on the EDT with [flow]'s value now and after every change, until [parent] is disposed. */
    fun <T> follow(flow: StateFlow<T>, parent: Disposable, render: (T) -> Unit) {
        val job = scope.launch(Dispatchers.EDT) { flow.collect { render(it) } }
        Disposer.register(parent) { job.cancel() }
    }

    override fun dispose() = Unit

    companion object {
        private val LOG = logger<AgentSessionsModel>()
        private val POLL = 2.seconds

        /** Past sessions change slowly; listing every transcript of every project each [POLL] would not. */
        private val HISTORY_POLL = 15.seconds

        /** Step X6.2, pure: the events worth telling — about sessions the IDE can bring up, in projects still listed. */
        fun <T> newsOf(events: List<AttentionEvent>, places: Map<String, SessionPlace<T>>, listed: Set<String>): List<AttentionEvent> =
            AgentNotifier.tellable(events, places).filter { it.basePath in listed }

        /** Step X6.5, pure: each project's base path → the sessions waiting for input there, named as their rows are. */
        fun waitingByProject(groups: List<ProjectGroup>): Map<String, List<String>> =
            groups.associate { group -> group.basePath to group.sessions.filter { it.session.status == WAITING }.map { SessionRowText.title(it.session) } }

        private const val WAITING = "waiting"

        fun getInstance(): AgentSessionsModel = service()
    }
}

/** Step X2.4. The processes under a shell, from one look at the process table per pass, taken only if someone asks. */
internal object ProcessTree {

    fun snapshot(): (Long) -> Set<Long> {
        val children by lazy {
            ProcessHandle.allProcesses().toList().mapNotNull { handle -> handle.parent().orElse(null)?.let { it.pid() to handle.pid() } }
                .groupBy({ it.first }, { it.second })
        }
        return { pid ->
            val found = HashSet<Long>()
            val stack = ArrayDeque(children[pid].orEmpty())
            while (stack.isNotEmpty()) {
                val next = stack.removeLast()
                if (found.add(next)) stack.addAll(children[next].orEmpty())
            }
            found
        }
    }
}
