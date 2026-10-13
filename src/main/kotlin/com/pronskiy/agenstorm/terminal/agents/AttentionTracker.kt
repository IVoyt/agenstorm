package com.pronskiy.agenstorm.terminal.agents

/** Step X6.1. Something a session did while its tab was not on screen, worth telling the user about (X6.2). */
data class AttentionEvent(val kind: Kind, val session: LiveSession, val basePath: String) {
    enum class Kind {
        /** Claude waits on the user: a permission, a question, a dialog ([LiveSession.waitingFor] says which). */
        NEEDS_INPUT,

        /** Claude stopped working. */
        FINISHED,
    }
}

/** One read's outcome: the sessions carrying a dot, and what happened since the read before. */
data class Attention(val finished: Set<String>, val events: List<AttentionEvent>)

/**
 * Steps X2.4 and X6.1. What the sessions did while the user was not looking (decision 107). A session that was `busy`
 * at one read and is neither busy nor `waiting` at the next — `idle`, `shell` or a status Claude may add later — has
 * *finished*; one that becomes `waiting` *needs input*. Both count only while its tab is not on screen ([OnScreen]),
 * and only as changes between two reads: the first read of a session raises nothing. A finished session keeps a dot
 * until it is clicked ([seen]), its tab comes on screen, it works or waits again, or it ends; a waiting one gets no
 * dot, it has its own mark. Fed one read at a time; thread-safe.
 */
class AttentionTracker {

    private val lastStatus = HashMap<String, String?>()
    private val finished = HashSet<String>()

    /** After a read: each running session with the base path it belongs to, and the ids on screen. */
    @Synchronized
    fun update(sessions: List<Pair<LiveSession, String>>, onScreen: Set<String>): Attention {
        val ids = sessions.mapTo(HashSet()) { it.first.sessionId }
        finished.retainAll(ids)
        lastStatus.keys.retainAll(ids)
        val events = mutableListOf<AttentionEvent>()
        for ((session, base) in sessions) {
            val id = session.sessionId
            val known = id in lastStatus
            val before = lastStatus[id]
            val away = id !in onScreen
            when {
                session.status == BUSY || session.status == WAITING || !away -> finished.remove(id)
                known && before == BUSY -> {
                    finished.add(id)
                    events += AttentionEvent(AttentionEvent.Kind.FINISHED, session, base)
                }
            }
            if (known && away && session.status == WAITING && before != WAITING) {
                events += AttentionEvent(AttentionEvent.Kind.NEEDS_INPUT, session, base)
            }
            lastStatus[id] = session.status
        }
        return Attention(finished.toSet(), events)
    }

    /** The finished ids now, with every [seen] since the last [update] taken into account. */
    @Synchronized
    fun current(): Set<String> = finished.toSet()

    @Synchronized
    fun seen(sessionId: String) {
        finished.remove(sessionId)
    }

    /** Forgets every session: reads stopped (the setting went off), so the next one is a first read again. */
    @Synchronized
    fun reset() {
        lastStatus.clear()
        finished.clear()
    }

    private companion object {
        const val BUSY = "busy"
        const val WAITING = "waiting"
    }
}
