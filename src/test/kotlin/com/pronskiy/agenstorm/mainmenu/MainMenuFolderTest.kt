package com.pronskiy.agenstorm.mainmenu

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Y1.4 against the IDE's real `MainMenu`: folded under *More* right after View, and put back exactly as it was. */
class MainMenuFolderTest : BasePlatformTestCase() {

    private val actionManager get() = ActionManager.getInstance()

    private fun topLevel(): List<String?> {
        val menu = actionManager.getAction(MainMenuFolder.MAIN_MENU_ID) as DefaultActionGroup
        return menu.getChildren(actionManager).map { actionManager.getId(it) }
    }

    override fun tearDown() {
        try {
            MainMenuFolder.unfold()
        } finally {
            super.tearDown()
        }
    }

    fun testFoldsTheSixUnderMoreAfterView() {
        val before = topLevel()
        val expected = MoreMenuPlan.FOLDED_IDS.filter { it in before }
        assertTrue("the test IDE's main menu has the menus to fold: $before", expected.isNotEmpty())

        MainMenuFolder.fold()

        val after = topLevel()
        assertTrue(MainMenuFolder.isFolded())
        assertEquals(after.indexOf("ViewMenu") + 1, after.indexOf(MainMenuFolder.MORE_ID))
        assertTrue(after.none { it in MoreMenuPlan.FOLDED_IDS })
        val more = actionManager.getAction(MainMenuFolder.MORE_ID) as MoreMenuGroup
        assertEquals(expected, more.getChildren(null).map { actionManager.getId(it) })
    }

    fun testUnfoldRestoresTheOriginalOrder() {
        val before = topLevel()

        MainMenuFolder.fold()
        MainMenuFolder.unfold()

        assertFalse(MainMenuFolder.isFolded())
        assertEquals(before, topLevel())
    }

    fun testFoldingTwiceIsANoOp() {
        MainMenuFolder.fold()
        val once = topLevel()

        MainMenuFolder.fold()

        assertEquals(once, topLevel())
    }

    fun testUnfoldingAMenuNeverFoldedLeavesItAlone() {
        val before = topLevel()

        MainMenuFolder.unfold()

        assertEquals(before, topLevel())
    }

    fun testTheGroupIsRegisteredAsAPopup() {
        val more = actionManager.getAction(MainMenuFolder.MORE_ID)
        assertTrue(more is MoreMenuGroup)
        assertTrue(more!!.templatePresentation.isPopupGroup)
    }
}
