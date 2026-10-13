package com.pronskiy.agenstorm.terminal.agents

import com.pronskiy.agenstorm.terminal.agents.AttentionEvent.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

/** Step X6.1 (X2.4 before it): what a session did while its tab was not on screen — a dot for finishing, an event for each change worth telling. */
class AttentionTrackerTest {

    private val tracker = AttentionTracker()

    private fun session(id: String, status: String?, waitingFor: String? = null) =
        LiveSession(pid = 1, sessionId = id, cwd = "/w/app", name = null, status = status, tmuxSession = null, updatedAt = null, entrypoint = null, waitingFor = waitingFor)

    private fun read(vararg sessions: LiveSession, onScreen: Set<String> = emptySet()): Attention =
        tracker.update(sessions.map { it to "/w/app" }, onScreen)

    private fun kinds(attention: Attention) = attention.events.map { it.session.sessionId to it.kind }

    @Test
    fun busyThenIdleOffScreenIsFinished() {
        read(session("a", "busy"))

        val attention = read(session("a", "idle"))

        assertEquals(setOf("a"), attention.finished)
        assertEquals(listOf("a" to Kind.FINISHED), kinds(attention))
        assertEquals("/w/app", attention.events.single().basePath)
    }

    @Test
    fun shellModeAndAnUnknownStatusAreIdleToo() {
        read(session("a", "busy"), session("b", "busy"))

        val attention = read(session("a", "shell"), session("b", "dozing"))

        assertEquals(setOf("a", "b"), attention.finished)
    }

    @Test
    fun finishingOnScreenIsNoNews() {
        read(session("a", "busy"), onScreen = setOf("a"))

        val attention = read(session("a", "idle"), onScreen = setOf("a"))

        assertEquals(emptySet<String>(), attention.finished)
        assertEquals(emptyList<AttentionEvent>(), attention.events)
    }

    @Test
    fun waitingOffScreenNeedsInputAndGetsNoDot() {
        read(session("a", "busy"))

        val attention = read(session("a", "waiting", waitingFor = "permission"))

        assertEquals("a waiting session keeps its own mark", emptySet<String>(), attention.finished)
        assertEquals(listOf("a" to Kind.NEEDS_INPUT), kinds(attention))
        assertEquals("permission", attention.events.single().session.waitingFor)
    }

    @Test
    fun waitingFromIdleNeedsInputToo() {
        read(session("a", "idle"))

        assertEquals(listOf("a" to Kind.NEEDS_INPUT), kinds(read(session("a", "waiting"))))
    }

    @Test
    fun waitingOnScreenIsNoNews() {
        read(session("a", "busy"))

        assertEquals(emptyList<AttentionEvent>(), read(session("a", "waiting"), onScreen = setOf("a")).events)
    }

    @Test
    fun stillWaitingIsNotToldTwice() {
        read(session("a", "busy"))
        read(session("a", "waiting"))

        assertEquals(emptyList<AttentionEvent>(), read(session("a", "waiting")).events)
    }

    @Test
    fun answeredWithoutWorkingIsNoNews() {
        read(session("a", "waiting"))

        val attention = read(session("a", "idle"))

        assertEquals(emptySet<String>(), attention.finished)
        assertEquals(emptyList<AttentionEvent>(), attention.events)
    }

    @Test
    fun theFirstReadOfASessionRaisesNothing() {
        val first = read(session("a", "waiting"), session("b", "idle"))
        read(session("a", "waiting"), session("b", "idle"))
        val later = read(session("a", "waiting"), session("b", "idle"), session("c", "waiting"))

        assertEquals(emptyList<AttentionEvent>(), first.events)
        assertEquals("a session first seen waiting", emptyList<AttentionEvent>(), later.events)
    }

    @Test
    fun theDotGoesOnScreenSeenBusyAgainWaitingOrGone() {
        fun finish() {
            read(session("a", "busy"))
            read(session("a", "idle"))
        }

        finish()
        assertEquals("a later idle read keeps it", setOf("a"), read(session("a", "idle")).finished)
        assertEquals("its tab came on screen", emptySet<String>(), read(session("a", "idle"), onScreen = setOf("a")).finished)

        finish()
        tracker.seen("a")
        assertEquals("clicked", emptySet<String>(), read(session("a", "idle")).finished)

        finish()
        assertEquals("busy again", emptySet<String>(), read(session("a", "busy")).finished)

        read(session("a", "idle"))
        assertEquals("waiting has its own mark", emptySet<String>(), read(session("a", "waiting")).finished)

        finish()
        assertEquals("gone", emptySet<String>(), tracker.update(emptyList(), emptySet()).finished)
    }

    @Test
    fun aGoneSessionStartsOverWhenItComesBack() {
        read(session("a", "busy"))
        tracker.update(emptyList(), emptySet())

        assertEquals(emptyList<AttentionEvent>(), read(session("a", "idle")).events)
    }

    @Test
    fun resetForgetsWhatWasRead() {
        read(session("a", "busy"), session("b", "busy"))
        read(session("a", "busy"), session("b", "idle"))

        tracker.reset()

        assertEquals(emptySet<String>(), tracker.current())
        assertEquals("the setting went off and on again", emptyList<AttentionEvent>(), read(session("a", "idle"), session("b", "idle")).events)
    }

    @Test
    fun currentSeesAClickSinceTheLastRead() {
        read(session("a", "busy"))
        read(session("a", "idle"))

        tracker.seen("a")

        assertEquals(emptySet<String>(), tracker.current())
    }
}
