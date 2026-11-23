/*
 * StatusBarLyric
 * Copyright (C) 2021-2022 fkj@fkj233.cn
 * https://github.com/Block-Network/StatusBarLyric
 *
 * This software is free opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as
 * published by Block-Network contributors.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/Block-Network/StatusBarLyric/blob/main/LICENSE>.
 */

package statusbar.lyric

import android.app.Application
import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class LyricApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) = ModuleService.onBind(service)
            override fun onServiceDied(service: XposedService) = ModuleService.onDied(service)
        })
    }
}

/**
 * Holds the framework service binder delivered through the libxposed XposedProvider.
 * The binder arrives asynchronously after process start, so callers wait via [awaitBinding].
 */
object ModuleService {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<() -> Unit>()

    @Volatile
    var service: XposedService? = null
        private set

    val isBound: Boolean get() = service != null

    fun onBind(service: XposedService) {
        mainHandler.post {
            // Multiple frameworks may each deliver a binder; keep the first one.
            if (this.service != null) return@post
            this.service = service
            flushPending()
        }
    }

    fun onDied(service: XposedService) {
        mainHandler.post {
            if (this.service === service) this.service = null
        }
    }

    /** Runs [callback] on the main thread once bound, or after [timeoutMs] if no framework shows up. */
    fun awaitBinding(timeoutMs: Long, callback: () -> Unit) {
        if (isBound) {
            callback()
            return
        }
        var done = false
        val once = {
            if (!done) {
                done = true
                callback()
            }
        }
        pending.add(once)
        mainHandler.postDelayed({
            pending.remove(once)
            once()
        }, timeoutMs)
    }

    private fun flushPending() {
        val callbacks = pending.toList()
        pending.clear()
        callbacks.forEach { it() }
    }
}
