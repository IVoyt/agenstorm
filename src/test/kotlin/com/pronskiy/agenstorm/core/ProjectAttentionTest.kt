package com.pronskiy.agenstorm.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Step X6.5: which projects have an agent waiting for input, for whoever draws them (the project tabs, X6.6). */
class ProjectAttentionTest {

    private val attention = ProjectAttention()

    @Test
    fun nothingWaitsAtFirst() {
        assertEquals(emptyMap<String, List<String>>(), attention.waiting.value)
    }

    @Test
    fun setKeepsOnlyProjectsWithSomeoneWaiting() {
        attention.set(mapOf("/w/alpha" to listOf("fix-auth", "docs"), "/w/beta" to emptyList()))

        assertEquals(mapOf("/w/alpha" to listOf("fix-auth", "docs")), attention.waiting.value)
    }

    @Test
    fun clearForgetsThemAll() {
        attention.set(mapOf("/w/alpha" to listOf("fix-auth")))

        attention.clear()

        assertEquals(emptyMap<String, List<String>>(), attention.waiting.value)
    }
}
