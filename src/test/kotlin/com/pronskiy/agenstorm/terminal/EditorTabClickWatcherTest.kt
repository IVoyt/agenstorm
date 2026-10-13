package com.pronskiy.agenstorm.terminal

import com.pronskiy.agenstorm.terminal.EditorTabClickWatcher.TabClick
import com.pronskiy.agenstorm.terminal.TerminalMaximizeToggleAction.TerminalWindowState
import junit.framework.TestCase

/** Steps J1.9 and J1.10: what a click on an editor tab asks for, given the terminal and how the editor came back. */
class EditorTabClickWatcherTest : TestCase() {

    private val maximized = TerminalWindowState(visible = true, maximized = true, docked = true)
    private val hidden = TerminalWindowState(visible = false, maximized = false, docked = true)
    private val split = TerminalWindowState(visible = true, maximized = false, docked = true)
    private val floatingHidden = TerminalWindowState(visible = false, maximized = false, docked = false)

    fun testAnyTabOverAMaximizedTerminalAsksForTheEditor() {
        for (armed in listOf(false, true)) {
            for (wasActive in listOf(false, true)) {
                assertEquals(TabClick.EDITOR, EditorTabClickWatcher.tabClick(maximized, armed, wasActive))
            }
        }
    }

    fun testTheActiveTabAsksForTheTerminalOnceATabClickGaveTheEditorBack() {
        assertEquals(TabClick.TERMINAL, EditorTabClickWatcher.tabClick(hidden, armed = true, wasActive = true))
    }

    fun testAnotherTabOnlySwitches() {
        assertEquals(TabClick.NONE, EditorTabClickWatcher.tabClick(hidden, armed = true, wasActive = false))
    }

    /** Leaving the terminal by the shortcut leaves the tab row alone. */
    fun testTheActiveTabDoesNothingUnlessATabClickGaveTheEditorBack() {
        assertEquals(TabClick.NONE, EditorTabClickWatcher.tabClick(hidden, armed = false, wasActive = true))
    }

    /** A terminal at its normal size is used beside the editor; the active tab is how the caret gets back to the file. */
    fun testTheActiveTabDoesNothingWhileTheTerminalIsShownBesideTheEditor() {
        assertEquals(TabClick.NONE, EditorTabClickWatcher.tabClick(split, armed = true, wasActive = true))
    }

    fun testTheActiveTabDoesNothingForAFloatingTerminal() {
        assertEquals(TabClick.NONE, EditorTabClickWatcher.tabClick(floatingHidden, armed = true, wasActive = true))
    }

    /**
     * The press on the active tab moves the keyboard into the editor, and J1.12 answers that by hiding the terminal
     * and arming the row before the click arrives. The click began over a maximized terminal, so it is still a click
     * for the editor, not one for the terminal back.
     */
    fun testAClickIsJudgedByTheTerminalAsItWasWhenTheMouseWentDown() {
        assertEquals(
            TabClick.EDITOR,
            EditorTabClickWatcher.tabClick(atPress = maximized, atClick = hidden, armed = true, wasActive = true),
        )
    }

    /** A click whose press the listener did not see — it began listening in between — goes by the terminal now. */
    fun testAClickWithoutItsPressGoesByTheTerminalNow() {
        assertEquals(
            TabClick.TERMINAL,
            EditorTabClickWatcher.tabClick(atPress = null, atClick = hidden, armed = true, wasActive = true),
        )
    }
}
