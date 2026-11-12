package net.automationse.relayharbor.notification

import kotlinx.coroutines.flow.MutableStateFlow

/** Notification-tap navigation requests, including when MainActivity already exists. */
object NotificationOpenRequests {
    val channelId = MutableStateFlow<Long?>(null)
    fun offer(id: Long) {
        if (id > 0) channelId.value = id
    }
    fun consume(id: Long) {
        if (channelId.value == id) channelId.value = null
    }
}
