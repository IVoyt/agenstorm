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
 * *finished* once it has stayed so for [settleMs]; one that becomes `waiting` *needs input*. Both count only while its
 * tab is not on screen ([OnScreen]), and only as changes between two reads: the first read of a session raises nothing.
 * The wait is for Claude Code's own gap: when its last background subagent ends it writes `idle`, then `busy` again to
 * read the subagent's answer (3 ms apart with 2.1.304), and a read landing in between is not the end of the task. A
 * finished session keeps a dot until it is clicked ([seen]), its tab comes on screen, it works or waits again, or it
 * ends; a waiting one gets no dot, it has its own mark. Fed one read at a time; thread-safe.
 */
class AttentionTracker(private val settleMs: Long = SETTLE_MS, private val clock: () -> Long = System::currentTimeMillis) {

    private val lastStatus = HashMap<String, String?>()
    private val finished = HashSet<String>()

    /** The sessions that stopped working while away, and when that was first read: finished once it lasts [settleMs]. */
    private val stopped = HashMap<String, Long>()

    /** After a read: each running session with the base path it belongs to, and the ids on screen. */
    @Synchronized
    fun update(sessions: List<Pair<LiveSession, String>>, onScreen: Set<String>): Attention {
        val now = clock()
        val ids = sessions.mapTo(HashSet()) { it.first.sessionId }
        finished.retainAll(ids)
        stopped.keys.retainAll(ids)
        lastStatus.keys.retainAll(ids)
        val events = mutableListOf<AttentionEvent>()
        for ((session, base) in sessions) {
            val id = session.sessionId
            val known = id in lastStatus
            val before = lastStatus[id]
            val away = id !in onScreen
            when {
                session.status == BUSY || session.status == WAITING || !away -> {
                    finished.remove(id)
                    stopped.remove(id)
                }
                known && before == BUSY -> stopped[id] = now
            }
            if (stopped[id]?.let { now - it >= settleMs } == true) {
                stopped.remove(id)
                finished.add(id)
                events += AttentionEvent(AttentionEvent.Kind.FINISHED, session, base)
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
        stopped.remove(sessionId)
    }

    /** Forgets every session: reads stopped (the setting went off), so the next one is a first read again. */
    @Synchronized
    fun reset() {
        lastStatus.clear()
        finished.clear()
        stopped.clear()
    }

    private companion object {
        const val SETTLE_MS = 3_000L
        const val BUSY = "busy"
        const val WAITING = "waiting"
    }
}
