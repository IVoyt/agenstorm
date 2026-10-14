package com.pronskiy.agenstorm.mainmenu

/**
 * Step Y1.4, the placement alone: which of the main menu's top-level entries go under *More*, where *More* goes, and
 * where each entry goes back. Ids only, so it is tested without an IDE.
 */
object MoreMenuPlan {

    /** Navigate, Code, Refactor, Run, Tools and the VCS menu ("Git"), in the order they appear under *More*. */
    val FOLDED_IDS: List<String> = listOf("GoToMenu", "CodeMenu", "RefactoringMenu", "RunMenu", "ToolsMenu", "VcsGroups")

    /** The stock order around the folded entries, for an unfold with no snapshot to go by. */
    val DEFAULT_ORDER: List<String> = listOf("FileMenu", "EditMenu", "ViewMenu") + FOLDED_IDS + listOf("WindowMenu", "HelpMenu")

    /**
     * What a fold does to a menu whose top level is [children] (ids in order, `null` for an entry without one):
     * [remove] the folded entries it has, in their order, and put *More* right after [moreAfter], the entry before the
     * first of them (`null`: at the very start).
     */
    data class Fold(val remove: List<String>, val moreAfter: String?)

    /** `null` when [children] holds none of [FOLDED_IDS]: nothing to fold. */
    fun fold(children: List<String?>): Fold? {
        val first = children.indexOfFirst { it in FOLDED_IDS }
        if (first < 0) return null
        val remove = children.filterNotNull().filter { it in FOLDED_IDS }
        val moreAfter = children.subList(0, first).lastOrNull { it != null }
        return Fold(remove, moreAfter)
    }

    /**
     * Where each of [folded] goes back, in [snapshot] order: after the nearest entry before it in [snapshot] that is
     * on the menu by then — one of [present], or an entry put back just before it. `null`: at the very start.
     */
    fun unfold(snapshot: List<String?>, folded: List<String>, present: Set<String>): List<Pair<String, String?>> {
        val onMenu = present.toMutableSet()
        val order = folded.sortedBy { snapshot.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        return order.map { id ->
            val at = snapshot.indexOf(id)
            val before = if (at < 0) snapshot else snapshot.subList(0, at)
            val after = before.lastOrNull { it != null && it in onMenu }
            onMenu += id
            id to after
        }
    }
}
