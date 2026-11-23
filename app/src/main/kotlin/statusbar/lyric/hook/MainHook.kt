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

import android.app.Application
import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import statusbar.lyric.BuildConfig
import statusbar.lyric.R
import statusbar.lyric.config.XposedOwnSP.config
import statusbar.lyric.hook.HookEnv.moduleRes
import statusbar.lyric.hook.module.SystemUILyric
import statusbar.lyric.hook.module.SystemUITest
import statusbar.lyric.tools.LogTools
import statusbar.lyric.tools.LogTools.log
import java.util.Locale

class MainHook : XposedModule() {
    private var processName: String = ""
    private var isApplicationAttached = false

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        HookEnv.attachModule(this)
        LogTools.sink = { message -> log(Log.DEBUG, LogTools.TAG, message) }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != SYSTEM_UI || !param.isFirstPackage) return
        if (processName != SYSTEM_UI) return

        HookEnv.classLoader = param.classLoader
        LogTools.init(config.outLog)

        // Resources and the SystemUI context only exist after Application.attach, so every
        // other hook is installed from there (still before SystemUIApplication.onCreate).
        Application::class.java.getDeclaredMethod("attach", Context::class.java).createHook {
            after { hookParam ->
                if (isApplicationAttached) return@after
                isApplicationAttached = true
                HookEnv.attachApplication(hookParam.thisObject as Context)
                onSystemUIAttached()
            }
        }
    }

    private fun onSystemUIAttached() {
        if (!config.masterSwitch) {
            moduleRes.getString(R.string.master_off).log()
            return
        }
        "${BuildConfig.APPLICATION_ID} - ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}[${Locale.getDefault().language}] *${BuildConfig.BUILD_TYPE}) on $frameworkName $frameworkVersion (API $apiVersion)".log()
        if (config.testMode) {
            moduleRes.getString(R.string.hook_page).log()
            initHooks(SystemUITest())
        } else {
            moduleRes.getString(R.string.lyric_mode).log()
            try {
                initHooks(SystemUILyric())
            } catch (t: Throwable) {
                t.log()
            }
        }
    }

    private fun initHooks(vararg hook: BaseHook) {
        hook.forEach {
            try {
                if (it.isInit) return
                it.init()
                it.isInit = true
                "${moduleRes.getString(R.string.hook_succeeded)}:${it.javaClass.simpleName}".log()
            } catch (e: Exception) {
                "${moduleRes.getString(R.string.hook_failed)}:${it.javaClass.simpleName}".log()
                e.log()
            }
        }
    }

    companion object {
        private const val SYSTEM_UI = "com.android.systemui"
    }
}
