package com.pronskiy.agenstorm.terminal.agents

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.pronskiy.agenstorm.terminal.agents.AttentionEvent.Kind

/** Step X6.2: one notification per read, saying who needs what, sent to the OS or to the window in front. */
class AgentNotifierTest : BasePlatformTestCase() {

    private val projects = mapOf("/w/alpha" to "alpha", "/w/beta" to "beta")

    private fun event(kind: Kind, name: String?, base: String = "/w/alpha", waitingFor: String? = null, id: String = "id-$name") = AttentionEvent(
        kind,
        LiveSession(pid = 1, sessionId = id, cwd = base, name = name, status = null, tmuxSession = null, updatedAt = null, entrypoint = null, waitingFor = waitingFor),
        base,
    )

    private fun text(vararg events: AttentionEvent) = AttentionText.of(events.toList()) { projects.getValue(it) }!!

    fun testOneSessionNeedingInputSaysWhatFor() {
        val notice = text(event(Kind.NEEDS_INPUT, "fix-auth", waitingFor = "permission"))

        assertEquals("'fix-auth' needs your input", notice.title)
        assertEquals("alpha · permission", notice.text)
    }

    fun testWithNoReasonOnlyTheProjectIsSaid() {
        assertEquals("alpha", text(event(Kind.NEEDS_INPUT, "fix-auth")).text)
    }

    fun testOneSessionFinished() {
        val notice = text(event(Kind.FINISHED, "docs", base = "/w/beta"))

        assertEquals("'docs' finished", notice.title)
        assertEquals("beta", notice.text)
    }

    fun testANamelessSessionIsTheStartOfItsId() {
        assertEquals("'cccccccc' finished", text(event(Kind.FINISHED, null, id = "cccccccc-3333-4333-8333-333333333333")).title)
    }

    fun testSeveralSessionsMakeOneNoticeWithTheWaitingFirst() {
        val notice = text(event(Kind.FINISHED, "docs", base = "/w/beta"), event(Kind.NEEDS_INPUT, "fix-auth"))

        assertEquals("2 sessions need attention", notice.title)
        assertEquals("fix-auth (alpha), docs (beta)", notice.text)
        assertEquals(listOf("fix-auth", "docs"), notice.sessions.map { it.session.name })
    }

    fun testSeveralOfOneKindSayWhichKind() {
        assertEquals("2 sessions need your input", text(event(Kind.NEEDS_INPUT, "a"), event(Kind.NEEDS_INPUT, "b")).title)
        assertEquals("3 sessions finished", text(event(Kind.FINISHED, "a"), event(Kind.FINISHED, "b"), event(Kind.FINISHED, "c")).title)
    }

    fun testNothingToSayIsNoNotice() {
        assertNull(AttentionText.of(emptyList()) { it })
    }

    fun testOnlySessionsTheIdeCanShowAreTold() {
        val inTab = event(Kind.FINISHED, "tab")
        val background = event(Kind.FINISHED, "bg")
        val outside = event(Kind.FINISHED, "out")
        val places = mapOf<String, SessionPlace<String>>(
            inTab.session.sessionId to SessionPlace.InTab("t"),
            background.session.sessionId to SessionPlace.Background("app-1"),
            outside.session.sessionId to SessionPlace.Elsewhere,
        )

        assertEquals(listOf(inTab, background), AgentNotifier.tellable(listOf(inTab, background, outside, event(Kind.FINISHED, "unknown")), places))
    }

    fun testTheIdeInTheBackgroundGetsAnOsNotificationThatOpensTheFirstSession() {
        val sink = RecordingSink()
        val opened = mutableListOf<String>()
        val notifier = AgentNotifier(sink, active = { false }, enabled = { true }, projectName = { projects.getValue(it) }, open = { opened += it.session.name!! })

        notifier.tell(listOf(event(Kind.FINISHED, "docs"), event(Kind.NEEDS_INPUT, "fix-auth")))

        assertEquals(listOf("2 sessions need attention"), sink.system)
        assertEquals(emptyList<String>(), sink.balloons)
        sink.openSystem()
        assertEquals("the one waiting comes first", listOf("fix-auth"), opened)
    }

    fun testTheIdeInFrontGetsABalloonWithAShowPerSession() {
        val sink = RecordingSink()
        val opened = mutableListOf<String>()
        val notifier = AgentNotifier(sink, active = { true }, enabled = { true }, projectName = { projects.getValue(it) }, open = { opened += it.session.name!! })

        notifier.tell(listOf(event(Kind.NEEDS_INPUT, "fix-auth")))

        assertEquals(listOf("'fix-auth' needs your input"), sink.balloons)
        assertEquals(emptyList<String>(), sink.system)
        sink.openBalloon(0)
        assertEquals(listOf("fix-auth"), opened)
    }

    fun testOffOrNothingToTellSendsNothing() {
        val sink = RecordingSink()

        AgentNotifier(sink, active = { true }, enabled = { false }, projectName = { it }, open = {}).tell(listOf(event(Kind.FINISHED, "docs")))
        AgentNotifier(sink, active = { true }, enabled = { true }, projectName = { it }, open = {}).tell(emptyList())

        assertEquals(emptyList<String>(), sink.balloons + sink.system)
    }

    fun testABalloonOffersAtMostThreeShows() {
        assertEquals(listOf("Show"), AgentNotifier.shows(text(event(Kind.FINISHED, "a"))).map { it.first })
        assertEquals(
            listOf("Show 'a'", "Show 'b'", "Show 'c'"),
            AgentNotifier.shows(text(event(Kind.FINISHED, "a"), event(Kind.FINISHED, "b"), event(Kind.FINISHED, "c"), event(Kind.FINISHED, "d"))).map { it.first },
        )
    }

    private class RecordingSink : NoticeSink {
        val system = mutableListOf<String>()
        val balloons = mutableListOf<String>()
        private var systemOpen: () -> Unit = {}
        private var balloonShows: List<Pair<String, AttentionEvent>> = emptyList()
        private var balloonOpen: (AttentionEvent) -> Unit = {}

        override fun system(notice: AttentionNotice, open: () -> Unit) {
            system += notice.title
            systemOpen = open
        }

        override fun balloon(notice: AttentionNotice, shows: List<Pair<String, AttentionEvent>>, open: (AttentionEvent) -> Unit) {
            balloons += notice.title
            balloonShows = shows
            balloonOpen = open
        }

        fun openSystem() = systemOpen()

        fun openBalloon(index: Int) = balloonOpen(balloonShows[index].second)
    }
}
