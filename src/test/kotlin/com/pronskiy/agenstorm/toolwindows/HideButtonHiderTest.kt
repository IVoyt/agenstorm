package com.pronskiy.agenstorm.toolwindows

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Y1.3: the Terminal header's Hide button is found by class name and hidden, again when its toolbar rebuilds, and
 * nothing else is touched. The platform's classes are internal, so classes of our own stand in for them by name.
 */
class HideButtonHiderTest : BasePlatformTestCase() {

    private class Decorator : JPanel(BorderLayout())
    private abstract class HeaderBase : JPanel()
    private class Header : HeaderBase()
    private class Hide : AnAction() {
        override fun actionPerformed(e: AnActionEvent) = Unit
    }
    private class More : AnAction() {
        override fun actionPerformed(e: AnActionEvent) = Unit
    }
    private class Button(private val action: AnAction) : JComponent(), AnActionHolder {
        override fun getAction(): AnAction = action
    }

    private val names = HideButtonHider.Names(Decorator::class.java.name, HeaderBase::class.java.name, Hide::class.java.name)
    private lateinit var hider: HideButtonHider
    private lateinit var decorator: Decorator
    private lateinit var toolbar: JPanel
    private lateinit var more: Button
    private lateinit var hide: Button
    private lateinit var content: JPanel

    override fun setUp() {
        super.setUp()
        hider = HideButtonHider(names)
        more = Button(More())
        hide = Button(Hide())
        toolbar = JPanel().apply {
            add(more)
            add(hide)
        }
        content = JPanel()
        decorator = Decorator().apply {
            add(Header().apply { add(toolbar) }, BorderLayout.NORTH)
            add(JPanel(BorderLayout()).apply { add(content) }, BorderLayout.CENTER)
        }
    }

    fun testHidesOnlyTheHideButton() {
        assertTrue(hider.sync(content, hide = true))

        assertFalse(hide.isVisible)
        assertTrue(more.isVisible)
    }

    fun testAButtonTheToolbarAddsLaterIsHiddenToo() {
        hider.sync(content, hide = true)
        toolbar.removeAll()
        val rebuilt = Button(Hide())
        toolbar.add(Button(More()))
        toolbar.add(rebuilt)

        assertFalse(rebuilt.isVisible)
    }

    fun testAButtonInsideANewContainerIsHiddenToo() {
        hider.sync(content, hide = true)
        val nested = Button(Hide())
        toolbar.add(JPanel().apply { add(nested) })

        assertFalse(nested.isVisible)
    }

    fun testShowsItAgainAndStopsWatching() {
        hider.sync(content, hide = true)
        hider.sync(content, hide = false)

        assertTrue(hide.isVisible)
        assertFalse(hider.isWatching())
        val later = Button(Hide())
        toolbar.add(later)
        assertTrue(later.isVisible)
    }

    fun testWithoutADecoratorNothingHappens() {
        val bare = JPanel()
        JPanel().apply { add(Header().apply { add(bare) }) }

        assertFalse(hider.sync(bare, hide = true))
        assertFalse(hider.isWatching())
    }

    fun testTheContentIsLeftAlone() {
        val inContent = Button(Hide())
        content.add(inContent)

        hider.sync(content, hide = true)

        assertTrue(inContent.isVisible)
    }

    fun testTheOutermostDecoratorOwnsTheHeader() {
        val cell = JPanel()
        val inner = Decorator().apply { add(cell) }
        content.add(inner)

        assertTrue(hider.sync(cell, hide = true))
        assertFalse(hide.isVisible)
    }

    fun testASplitCellIsReportedAndItsHeaderHiddenOnTheNextSync() {
        var reported = 0
        hider.sync(content, hide = true) { reported++ }
        val cellHide = Button(Hide())
        decorator.add(Decorator().apply { add(Header().apply { add(JPanel().apply { add(cellHide) }) }) })

        assertEquals(1, reported)
        assertTrue("not yet: the new cell's header is reached by the next sync", cellHide.isVisible)
        hider.sync(content, hide = true) { reported++ }
        assertFalse(cellHide.isVisible)
    }

    fun testShowingStopsWatchingTheDecoratorsToo() {
        var reported = 0
        hider.sync(content, hide = true) { reported++ }
        hider.sync(content, hide = false) { reported++ }

        decorator.add(JPanel())

        assertEquals(0, reported)
        assertFalse(hider.isWatching())
    }

    /** The platform's names are spelled out in one place; this keeps the spelling honest on the IDE we build against. */
    fun testThePlatformClassesExistUnderTheirNames() {
        for (name in listOf(HideButtonHider.DECORATOR_CLASS, HideButtonHider.HEADER_CLASS, HideButtonHider.HIDE_ACTION_CLASS)) {
            assertNotNull(name, Class.forName(name, false, javaClass.classLoader))
        }
    }
}
