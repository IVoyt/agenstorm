package com.pronskiy.agenstorm.mainmenu

import com.intellij.ide.ui.customization.ActionUrl
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Anchor
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAwareAction
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

    fun testAMenuAddedWhileFoldedStaysAndTheStockOrderComesBack() {
        val menu = actionManager.getAction(MainMenuFolder.MAIN_MENU_ID) as DefaultActionGroup
        val before = topLevel()
        val afterView = DumbAwareAction.create("Extra after View") {}
        val afterTools = DumbAwareAction.create("Extra after Tools") {}
        MainMenuFolder.fold()
        try {
            menu.add(afterView, Constraints(Anchor.AFTER, "ViewMenu"), actionManager)
            // Tools is under More now, so this one has no anchor until the unfold puts Tools back.
            menu.add(afterTools, Constraints(Anchor.AFTER, "ToolsMenu"), actionManager)

            MainMenuFolder.unfold()

            val children = menu.getChildren(actionManager).toList()
            assertEquals(before, children.filter { it !== afterView && it !== afterTools }.map { actionManager.getId(it) })
            assertTrue(afterView in children)
            assertTrue(children.indexOf(afterTools) > children.indexOfFirst { actionManager.getId(it) == "ToolsMenu" })
        } finally {
            menu.remove(afterView, actionManager)
            menu.remove(afterTools, actionManager)
        }
    }

    /** A Menus and Toolbars customization of Tools, saved before the fold, still applies to Tools under More. */
    fun testMoreKeepsAMenusAndToolbarsCustomizationOfTools() {
        val tools = actionManager.getAction("ToolsMenu") as? DefaultActionGroup ?: return fail("no ToolsMenu in the test IDE")
        val toolsText = tools.templatePresentation.text
        val items = tools.getChildren(actionManager).toList()
        val victim = items.first { it !is Separator && it !is ActionGroup && actionManager.getId(it) != null }
        val schema = CustomActionsSchema.getInstance()
        val saved = ArrayList(schema.getActions())
        val root = schema.getDisplayName(MainMenuFolder.MAIN_MENU_ID) ?: "Main Menu"
        // Test-only: the schema's own setters are internal API, which the plugin itself never calls. The settings page
        // bumps the stamp when it applies, so this does too.
        schema.setActions(listOf(ActionUrl(arrayListOf("root", root, toolsText), actionManager.getId(victim), ActionUrl.DELETED, items.indexOf(victim))))
        schema.incrementModificationStamp()
        try {
            MainMenuFolder.fold()

            val more = actionManager.getAction(MainMenuFolder.MORE_ID) as MoreMenuGroup
            val toolsUnderMore = more.getChildren(null).first { it.templatePresentation.text == toolsText } as ActionGroup
            val shown = toolsUnderMore.getChildren(null).toList()
            assertFalse("the item removed in Menus and Toolbars stays removed", victim in shown)
            assertEquals(items.size - 1, shown.size)
        } finally {
            schema.setActions(saved)
            schema.incrementModificationStamp()
        }
    }

    fun testTheGroupIsRegisteredAsAPopup() {
        val more = actionManager.getAction(MainMenuFolder.MORE_ID)
        assertTrue(more is MoreMenuGroup)
        assertTrue(more!!.templatePresentation.isPopupGroup)
    }
}
