package net.automationse.relayharbor.floating

import kotlinx.coroutines.flow.MutableStateFlow

/** Consume a deliberate edge-launcher tab request once (including singleTask). */
object FloatingNavigationRequests {
    val page = MutableStateFlow<String?>(null)

    fun offer(value: String?) {
        page.value = FloatingMenuPolicy.validTab(value)
    }

    fun consume(value: String) {
        if (page.value == value) page.value = null
    }
}
