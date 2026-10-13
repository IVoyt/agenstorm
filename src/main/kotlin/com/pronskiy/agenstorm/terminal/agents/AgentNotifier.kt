package com.pronskiy.agenstorm.terminal.agents

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.SystemNotifications
import com.pronskiy.agenstorm.core.AgenstormBundle
import com.pronskiy.agenstorm.core.AgenstormNotifications
import com.pronskiy.agenstorm.core.AgenstormSettings
import com.pronskiy.agenstorm.terminal.agents.AttentionEvent.Kind

/** Step X6.2. What one read's events say: a title, a line below it, and the sessions it is about, the waiting ones first. */
data class AttentionNotice(val title: String, val text: String, val sessions: List<AttentionEvent>)

/** Step X6.2. Pure: one read's events → one notice, or none when there is nothing to say. */
object AttentionText {

    fun of(events: List<AttentionEvent>, projectName: (String) -> String): AttentionNotice? {
        if (events.isEmpty()) return null
        val sessions = events.sortedBy { if (it.kind == Kind.NEEDS_INPUT) 0 else 1 }
        val one = sessions.singleOrNull()
        if (one != null) {
            val name = SessionRowText.title(one.session)
            val project = projectName(one.basePath)
            return when (one.kind) {
                Kind.NEEDS_INPUT -> AttentionNotice(
                    AgenstormBundle.message("agents.notify.needsInput.one", name),
                    one.session.waitingFor?.let { AgenstormBundle.message("agents.notify.reason", project, it) } ?: project,
                    sessions,
                )
                Kind.FINISHED -> AttentionNotice(AgenstormBundle.message("agents.notify.finished.one", name), project, sessions)
            }
        }
        val title = when (sessions.map { it.kind }.toSet().singleOrNull()) {
            Kind.NEEDS_INPUT -> AgenstormBundle.message("agents.notify.needsInput.many", sessions.size)
            Kind.FINISHED -> AgenstormBundle.message("agents.notify.finished.many", sessions.size)
            null -> AgenstormBundle.message("agents.notify.mixed.many", sessions.size)
        }
        val text = sessions.joinToString(", ") { AgenstormBundle.message("agents.notify.session", SessionRowText.title(it.session), projectName(it.basePath)) }
        return AttentionNotice(title, text, sessions)
    }
}

/** Step X6.2. Where a notice goes; the IDE's in [IdeNoticeSink], a recording one in tests. */
interface NoticeSink {
    /** An OS notification; [open] runs when it is clicked. */
    fun system(notice: AttentionNotice, open: () -> Unit)

    /** A balloon in the window in front, with one action per entry of [shows]: its text and the session it opens. */
    fun balloon(notice: AttentionNotice, shows: List<Pair<String, AttentionEvent>>, open: (AttentionEvent) -> Unit)
}

/**
 * Step X6.2. Tells the user what [AttentionTracker] found, once per read (decision 107): an OS notification while the
 * IDE is in the background, a balloon in the window in front otherwise — the IDE is in front, but not the session's
 * tab. Either one opens the session the way a click on its row does. Only sessions the IDE can bring up are told
 * ([tellable]); the setting [AgenstormSettings.State.agentSessionsNotify] turns it all off. No sound.
 */
class AgentNotifier(
    private val sink: NoticeSink = IdeNoticeSink,
    private val active: () -> Boolean = { ApplicationManager.getApplication().isActive },
    private val enabled: () -> Boolean = { AgenstormSettings.getInstance().state.agentSessionsNotify },
    private val projectName: (String) -> String,
    private val open: (AttentionEvent) -> Unit,
) {

    fun tell(events: List<AttentionEvent>) {
        if (events.isEmpty() || !enabled()) return
        val notice = AttentionText.of(events, projectName) ?: return
        if (active()) sink.balloon(notice, shows(notice), open) else sink.system(notice) { open(notice.sessions.first()) }
    }

    companion object {
        private const val MAX_SHOWS = 3

        /** The events about sessions the IDE can bring up: in a Terminal tab or a background terminal, not outside it. */
        fun <T> tellable(events: List<AttentionEvent>, places: Map<String, SessionPlace<T>>): List<AttentionEvent> =
            events.filter { places[it.session.sessionId].let { place -> place != null && place != SessionPlace.Elsewhere } }

        /** A balloon's actions: *Show* for one session, *Show 'name'* for each of the first three of several. */
        fun shows(notice: AttentionNotice): List<Pair<String, AttentionEvent>> = notice.sessions.singleOrNull()
            ?.let { listOf(AgenstormBundle.message("agents.notify.show") to it) }
            ?: notice.sessions.take(MAX_SHOWS).map { AgenstormBundle.message("agents.notify.showSession", SessionRowText.title(it.session)) to it }
    }
}

/** Step X6.2. The IDE's two ways of telling: the platform's OS notifications and the plugin's balloon group. */
object IdeNoticeSink : NoticeSink {

    /** The OS notification's name; macOS shows the IDE as its sender either way. */
    private const val NAME = "Agenstorm Agents"

    override fun system(notice: AttentionNotice, open: () -> Unit) {
        // The click may come on whatever thread the OS answers on.
        SystemNotifications.getInstance().notify(NAME, notice.title, notice.text) { ApplicationManager.getApplication().invokeLater(open) }
    }

    override fun balloon(notice: AttentionNotice, shows: List<Pair<String, AttentionEvent>>, open: (AttentionEvent) -> Unit) {
        val notification = AgenstormNotifications.group().createNotification(notice.title, notice.text, NotificationType.INFORMATION)
        shows.forEach { (text, event) -> notification.addAction(NotificationAction.createSimpleExpiring(text) { open(event) }) }
        notification.notify(AgentsSidebarSync.front())
    }
}
