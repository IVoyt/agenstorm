package com.pronskiy.agenstorm.tabs.git

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.pronskiy.agenstorm.core.AgenstormSettings

/** The Git branch at the top, before the project tabs: opt-in, and the stock order back when it is off. */
class BranchToolbarPlacementTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        AgenstormSettings.getInstance().loadState(AgenstormSettings.State())
    }

    override fun tearDown() {
        try {
            BranchToolbarPlacement.sync(false)
            AgenstormSettings.getInstance().loadState(AgenstormSettings.State())
        } finally {
            super.tearDown()
        }
    }

    fun testOnTopTheBranchComesBeforeTheTabsAndTheRestKeepsItsOrder() {
        val stock = leftIds()
        assertTrue("stock: $stock", stock.indexOf(VcsToolbarGroup.ID) > stock.indexOf(BranchToolbarPlacement.PROJECT_ID))

        BranchToolbarPlacement.sync(true)

        assertEquals(VcsToolbarGroup.ID, leftIds().first())
        assertEquals(stock - VcsToolbarGroup.ID, leftIds().drop(1))
    }

    fun testBackInTheStatusBarTheStockOrderReturns() {
        val stock = leftIds()
        BranchToolbarPlacement.sync(true)
        BranchToolbarPlacement.sync(false)
        assertEquals(stock, leftIds())
    }

    fun testSyncingTwiceChangesNothing() {
        val stock = leftIds()
        BranchToolbarPlacement.sync(true)
        BranchToolbarPlacement.sync(true)
        assertEquals(1, leftIds().count { it == VcsToolbarGroup.ID })
        assertEquals(VcsToolbarGroup.ID, leftIds().first())
        BranchToolbarPlacement.sync(false)
        BranchToolbarPlacement.sync(false)
        assertEquals(stock, leftIds())
    }

    fun testOnlyTheTopChoiceWithTabsOnMovesIt() {
        val stock = leftIds()
        val state = AgenstormSettings.getInstance().state

        BranchToolbarPlacement.sync()
        assertEquals("the status bar is the default", stock, leftIds())

        state.branchInStatusBar = false
        BranchToolbarPlacement.sync()
        assertEquals(VcsToolbarGroup.ID, leftIds().first())

        state.projectTabsEnabled = false
        BranchToolbarPlacement.sync()
        assertEquals("no tabs, the stock toolbar", stock, leftIds())
    }

    private fun leftIds(): List<String?> {
        val actionManager = ActionManager.getInstance()
        val left = actionManager.getAction(BranchToolbarPlacement.LEFT_ID) as DefaultActionGroup
        return left.getChildren(actionManager).map { actionManager.getId(it) }
    }
}
