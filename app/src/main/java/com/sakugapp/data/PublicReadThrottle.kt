// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared by feed/classification and autocomplete reads; cancellation never schedules a retry. */
internal object PublicReadThrottle {
    private val mutex=Mutex()
    private var last=0L
    suspend fun awaitTurn() = mutex.withLock {
        val remaining=650L-(System.nanoTime()/1_000_000-last)
        if(remaining>0)delay(remaining)
        last=System.nanoTime()/1_000_000
    }
}
