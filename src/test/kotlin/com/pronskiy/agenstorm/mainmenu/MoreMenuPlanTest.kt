package com.pronskiy.agenstorm.mainmenu

import junit.framework.TestCase

/** Y1.4: which top-level entries fold under *More*, where *More* goes, and where each entry goes back. */
class MoreMenuPlanTest : TestCase() {

    private val phpStorm = listOf(
        "FileMenu", "EditMenu", "ViewMenu", "GoToMenu", "CodeMenu", "RefactoringMenu", "BuildMenu", "RunMenu",
        "ToolsMenu", "VcsGroups", "WindowMenu", "HelpMenu",
    )

    fun testFoldsTheSixAndPutsMoreWhereNavigateWas() {
        val fold = MoreMenuPlan.fold(phpStorm)!!

        assertEquals(MoreMenuPlan.FOLDED_IDS, fold.remove)
        assertEquals("ViewMenu", fold.moreAfter)
    }

    fun testAPluginMenuBetweenThemStaysOnTheBar() {
        val withLaravel = phpStorm.toMutableList().apply { add(indexOf("CodeMenu") + 1, "LaravelIdea") }

        val fold = MoreMenuPlan.fold(withLaravel)!!

        assertFalse("LaravelIdea" in fold.remove)
        assertFalse("BuildMenu" in fold.remove)
        assertEquals("ViewMenu", fold.moreAfter)
    }

    fun testMissingEntriesAreSkipped() {
        val fold = MoreMenuPlan.fold(listOf("FileMenu", "EditMenu", "ViewMenu", "ToolsMenu", "WindowMenu"))!!

        assertEquals(listOf("ToolsMenu"), fold.remove)
        assertEquals("ViewMenu", fold.moreAfter)
    }

    fun testNothingToFold() {
        assertNull(MoreMenuPlan.fold(listOf("FileMenu", "HelpMenu")))
    }

    fun testMoreGoesFirstWhenNothingPrecedesTheFoldedEntries() {
        assertNull(MoreMenuPlan.fold(listOf(null, "GoToMenu", "HelpMenu"))!!.moreAfter)
        assertEquals("FileMenu", MoreMenuPlan.fold(listOf("FileMenu", null, "GoToMenu"))!!.moreAfter)
    }

    fun testUnfoldPutsEveryEntryBackInItsPlace() {
        val withLaravel = phpStorm.toMutableList().apply { add(indexOf("CodeMenu") + 1, "LaravelIdea") }
        val fold = MoreMenuPlan.fold(withLaravel)!!
        val rest = withLaravel.filter { it !in fold.remove }

        val back = MoreMenuPlan.unfold(withLaravel, fold.remove, rest.toSet())

        assertEquals(withLaravel, replay(rest, back))
    }

    fun testUnfoldSkipsAnAnchorThatIsGone() {
        val snapshot = listOf("FileMenu", "ViewMenu", "GoToMenu", "LaravelIdea", "CodeMenu", "HelpMenu")
        val present = setOf("FileMenu", "ViewMenu", "HelpMenu")

        val back = MoreMenuPlan.unfold(snapshot, listOf("CodeMenu", "GoToMenu"), present)

        assertEquals(listOf("GoToMenu" to "ViewMenu", "CodeMenu" to "GoToMenu"), back)
    }

    fun testUnfoldWithoutAnyAnchorGoesFirst() {
        assertEquals(listOf("GoToMenu" to null), MoreMenuPlan.unfold(listOf("GoToMenu"), listOf("GoToMenu"), emptySet()))
    }

    /** Applies "id after anchor" moves the way `DefaultActionGroup` does with `Constraints(AFTER, anchor)`. */
    private fun replay(start: List<String>, moves: List<Pair<String, String?>>): List<String> {
        val list = start.toMutableList()
        for ((id, after) in moves) list.add(if (after == null) 0 else list.indexOf(after) + 1, id)
        return list
    }
}
