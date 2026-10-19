package com.pronskiy.agenstorm.tabs.git

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.pronskiy.agenstorm.core.AgenstormSettings

/** Step E3.1: the toolbar's VCS group is ours, keeps the stock children, and hides while the branch is in the status bar. */
class VcsToolbarGroupTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        AgenstormSettings.getInstance().loadState(AgenstormSettings.State())
    }

    override fun tearDown() {
        try {
            AgenstormSettings.getInstance().loadState(AgenstormSettings.State())
        } finally {
            super.tearDown()
        }
    }

    fun testOverridesTheStockGroupAndStillListsTheBranchWidget() {
        val group = ActionManager.getInstance().getAction(VcsToolbarGroup.ID)
        assertTrue("$group", group is VcsToolbarGroup)
        val ids = (group as VcsToolbarGroup).getChildren(null).map { ActionManager.getInstance().getId(it) }
        assertTrue("children: $ids", "main.toolbar.git.Branches" in ids)
    }

    /**
     * Reported by a user who put the branch back in the toolbar: Share Project On…, Create Git Repository and Show
     * Project History stood there as three buttons. `Vcs.ToolbarWidget.CreateRepository` is not one of the stock
     * group's children but a plain group the branch widget opens for a project without a repository.
     */
    fun testListsOnlyWhatTheStockGroupHolds() {
        val group = ActionManager.getInstance().getAction(VcsToolbarGroup.ID) as VcsToolbarGroup
        val ids = group.getChildren(null).map { ActionManager.getInstance().getId(it) }
        assertFalse("children: $ids", "Vcs.ToolbarWidget.CreateRepository" in ids)
        assertTrue("children: $ids", ids.all { it in listOf("main.toolbar.git.Branches", "main.toolbar.git.MergeRebase") })
    }

    fun testHiddenWhileTheBranchLivesInTheStatusBarAndVisibleOtherwise() {
        val group = VcsToolbarGroup()
        val event = TestActionEvent.createTestEvent(group, SimpleDataContext.getProjectContext(project))

        group.update(event)
        assertFalse(event.presentation.isVisible)

        AgenstormSettings.getInstance().state.branchInStatusBar = false
        group.update(event)
        assertTrue(event.presentation.isVisible)

        AgenstormSettings.getInstance().state.branchInStatusBar = true
        AgenstormSettings.getInstance().state.projectTabsEnabled = false
        group.update(event)
        assertTrue("tabs off means the stock toolbar", event.presentation.isVisible)
    }
}
