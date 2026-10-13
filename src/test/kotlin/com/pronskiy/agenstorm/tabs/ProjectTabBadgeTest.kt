package com.pronskiy.agenstorm.tabs

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.pronskiy.agenstorm.core.ProjectAttention
import com.pronskiy.agenstorm.tabs.ui.ProjectTabLabel
import com.pronskiy.agenstorm.tabs.ui.ProjectTabsPanel

/** Step X6.6: an orange dot on a project's tab while an agent in that project waits for input, and its names in the tooltip. */
class ProjectTabBadgeTest : BasePlatformTestCase() {

    private fun label(showIcon: Boolean = true) = ProjectTabLabel(ProjectTab.Loaded(project), selected = false, onSelect = {}, onClose = {}, showIcon = showIcon)

    override fun tearDown() {
        try {
            ProjectTabsModel.getInstance().loadState(ProjectTabsModel.State())
            ProjectAttention.getInstance().clear()
        } finally {
            super.tearDown()
        }
    }

    fun testNobodyWaitingIsNoBadge() {
        val label = label()

        assertFalse(label.hasBadge)
        assertEquals(project.basePath, label.toolTipText)
    }

    fun testOneWaitingGetsTheDotAndItsNameInTheTooltip() {
        val label = label()
        val width = label.preferredSize.width

        label.waiting = listOf("fix-auth")

        assertTrue(label.hasBadge)
        assertTrue(label.toolTipText, label.toolTipText.contains("Claude needs your input: fix-auth"))
        assertTrue("the path stays", label.toolTipText.contains(project.basePath!!))
        assertEquals("the dot sits on the icon, so the tab keeps its width", width, label.preferredSize.width)
    }

    fun testSeveralWaitingAreAllNamed() {
        val label = label()

        label.waiting = listOf("fix-auth", "docs")

        assertTrue(label.toolTipText.contains("Claude needs your input: fix-auth, docs"))
    }

    fun testTheDotGoesWhenNobodyWaitsAnyMore() {
        val label = label()
        val plain = label.nameIcon

        label.waiting = listOf("fix-auth")
        label.waiting = emptyList()

        assertFalse(label.hasBadge)
        assertSame(plain, label.nameIcon)
        assertEquals(project.basePath, label.toolTipText)
    }

    fun testWithoutTabIconsTheDotGoesBeforeTheName() {
        val label = label(showIcon = false)
        assertNull(label.nameIcon)

        label.waiting = listOf("fix-auth")

        assertNotNull(label.nameIcon)
        assertTrue(label.hasBadge)
    }

    fun testCompactTabsKeepTheDot() {
        val label = label()
        label.waiting = listOf("fix-auth")

        label.isCompact = true

        assertTrue(label.hasBadge)
        assertNotNull(label.nameIcon)
        assertTrue(label.toolTipText.contains("Claude needs your input: fix-auth"))
    }

    fun testTheStripFollowsTheProjectsWhereAnAgentWaits() {
        val panel = ProjectTabsPanel(ProjectTabsModel.getInstance())
        panel.attach()
        try {
            ProjectAttention.getInstance().set(mapOf(project.basePath!! to listOf("fix-auth")))
            PlatformTestUtil.waitWithEventsDispatching("the tab never got its dot", { panel.tabLabels().single { it.project === project }.hasBadge }, 5)

            ProjectAttention.getInstance().clear()
            PlatformTestUtil.waitWithEventsDispatching("the dot never went", { !panel.tabLabels().single { it.project === project }.hasBadge }, 5)
        } finally {
            panel.detach()
        }
    }

    fun testARebuiltStripKeepsTheDot() {
        ProjectAttention.getInstance().set(mapOf(project.basePath!! to listOf("fix-auth")))
        val panel = ProjectTabsPanel(ProjectTabsModel.getInstance())

        assertTrue(panel.tabLabels().single { it.project === project }.hasBadge)
    }

    fun testAnOffloadedTabNeverGetsOne() {
        val label = ProjectTabLabel(ProjectTab.Offloaded("/w/gone", "gone", sinceMs = 0), selected = false, onSelect = {}, onClose = {})

        label.waiting = listOf("fix-auth")

        assertFalse(label.hasBadge)
    }
}
