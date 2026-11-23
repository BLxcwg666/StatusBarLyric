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

package statusbar.lyric.tools

import android.content.SharedPreferences

class ConfigTools(private val mSP: SharedPreferences?, private val readOnly: Boolean = false) {

    // RemotePreferences are pushed by the framework, nothing to reload manually.
    fun reload() {}

    // A fresh editor per write: RemotePreferences.Editor never resets its pending state after apply().
    fun put(key: String?, any: Any) {
        if (readOnly) return
        val editor = mSP?.edit() ?: return
        when (any) {
            is Int -> editor.putInt(key, any)
            is String -> editor.putString(key, any)
            is Boolean -> editor.putBoolean(key, any)
            is Float -> editor.putFloat(key, any)
            else -> return
        }
        editor.apply()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> opt(key: String, defValue: T): T {
        val sp = mSP ?: return defValue
        return try {
            when (defValue) {
                is String -> sp.getString(key, defValue.toString()) as T
                is Int -> sp.getInt(key, defValue) as T
                is Boolean -> sp.getBoolean(key, defValue) as T
                is Double -> sp.getFloat(key, defValue.toFloat()) as T
                is Float -> sp.getFloat(key, defValue) as T
                else -> "" as T
            }
        } catch (_: ClassCastException) {
            defValue
        }
    }

    fun registerOnChange(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        mSP?.registerOnSharedPreferenceChangeListener(listener)
    }

    fun clearConfig() {
        if (readOnly) return
        mSP?.edit()?.clear()?.apply()
    }
}
