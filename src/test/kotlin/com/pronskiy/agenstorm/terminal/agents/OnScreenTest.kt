package com.pronskiy.agenstorm.terminal.agents

import org.junit.Assert.assertEquals
import org.junit.Test

/** Step X6.1: a session is on screen only while the IDE is in front and its tab is the one its visible Terminal shows. */
class OnScreenTest {

    private val places: Map<String, SessionPlace<String>> = mapOf(
        "a" to SessionPlace.InTab("tab-a"),
        "b" to SessionPlace.InTab("tab-b"),
        "bg" to SessionPlace.Background("app-1a2b"),
        "out" to SessionPlace.Elsewhere,
    )

    @Test
    fun theSelectedTabsSessionIsOnScreen() {
        assertEquals(setOf("a"), OnScreen.ids(places, OnScreen.Screen(active = true, terminalVisible = true, selectedTab = "tab-a")))
    }

    @Test
    fun nothingIsOnScreenWhileTheIdeIsBehindAnotherApp() {
        assertEquals(emptySet<String>(), OnScreen.ids(places, OnScreen.Screen(active = false, terminalVisible = true, selectedTab = "tab-a")))
    }

    @Test
    fun nothingIsOnScreenWhileTheTerminalIsHidden() {
        assertEquals(emptySet<String>(), OnScreen.ids(places, OnScreen.Screen(active = true, terminalVisible = false, selectedTab = "tab-a")))
    }

    @Test
    fun aTabNoSessionRunsInShowsNone() {
        assertEquals(emptySet<String>(), OnScreen.ids(places, OnScreen.Screen(active = true, terminalVisible = true, selectedTab = "tab-z")))
        assertEquals(emptySet<String>(), OnScreen.ids(places, OnScreen.Screen<String>(active = true, terminalVisible = true, selectedTab = null)))
    }

    @Test
    fun nobodyIsOnScreenWhenNothingIsShown() {
        assertEquals(emptySet<String>(), OnScreen.ids(places, OnScreen.Screen.nothing()))
    }
}
