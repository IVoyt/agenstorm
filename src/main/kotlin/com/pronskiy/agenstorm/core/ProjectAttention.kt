package com.pronskiy.agenstorm.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Step X6.5. Which open projects have an agent waiting for input, by base path, with the names of the sessions that
 * wait. A state, not an event: the Agents model (`terminal/agents/`) writes it after every read, whatever is on screen,
 * and the project tabs (`tabs/`, X6.6) draw it — the two features meet here because neither may import the other
 * (decision 107). Empty while the Agents sidebar or its tab badge is off.
 */
@Service(Service.Level.APP)
class ProjectAttention {

    private val flow = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    val waiting: StateFlow<Map<String, List<String>>> = flow.asStateFlow()

    /** Replaces what waits where; a project with nobody waiting is left out. Any thread. */
    fun set(waiting: Map<String, List<String>>) {
        flow.value = waiting.filterValues { it.isNotEmpty() }
    }

    fun clear() = set(emptyMap())

    companion object {
        fun getInstance(): ProjectAttention = service()
    }
}
