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

package statusbar.lyric.hook

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import io.github.libxposed.api.XposedModule
import statusbar.lyric.BuildConfig
import statusbar.lyric.tools.LogTools.log

@SuppressLint("StaticFieldLeak")
object HookEnv {
    lateinit var module: XposedModule
        private set
    lateinit var classLoader: ClassLoader
    lateinit var appContext: Context
        private set
    lateinit var moduleRes: Resources
        private set

    fun attachModule(module: XposedModule) {
        this.module = module
    }

    fun attachApplication(context: Context) {
        appContext = context
        moduleRes = try {
            context.packageManager.getResourcesForApplication(module.moduleApplicationInfo)
        } catch (t: Throwable) {
            t.log()
            context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY).resources
        }
    }

    fun remotePreferences(group: String): SharedPreferences? {
        return try {
            module.getRemotePreferences(group)
        } catch (t: Throwable) {
            t.log()
            null
        }
    }
}
