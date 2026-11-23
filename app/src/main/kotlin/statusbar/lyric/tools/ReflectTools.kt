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

import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Plain reflection replacement for the legacy XposedHelpers field / method helpers.
 */
object ReflectTools {
    private val fieldCache = ConcurrentHashMap<Pair<Class<*>, String>, Field>()
    private val missingFields = ConcurrentHashMap.newKeySet<Pair<Class<*>, String>>()

    private val boxedTypes = mapOf<Class<*>, Class<*>>(
        java.lang.Boolean.TYPE to java.lang.Boolean::class.java,
        java.lang.Byte.TYPE to java.lang.Byte::class.java,
        java.lang.Character.TYPE to java.lang.Character::class.java,
        java.lang.Short.TYPE to java.lang.Short::class.java,
        java.lang.Integer.TYPE to java.lang.Integer::class.java,
        java.lang.Long.TYPE to java.lang.Long::class.java,
        java.lang.Float.TYPE to java.lang.Float::class.java,
        java.lang.Double.TYPE to java.lang.Double::class.java,
    )

    fun findFieldOrNull(clazz: Class<*>, name: String): Field? {
        val key = clazz to name
        fieldCache[key]?.let { return it }
        if (key in missingFields) return null
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val field = current.getDeclaredField(name).apply { isAccessible = true }
                fieldCache[key] = field
                return field
            } catch (_: NoSuchFieldException) {
            }
            current = current.superclass
        }
        missingFields.add(key)
        return null
    }

    fun findField(clazz: Class<*>, name: String): Field =
        findFieldOrNull(clazz, name) ?: throw NoSuchFieldError("${clazz.name}#$name")

    private fun isCompatible(paramType: Class<*>, arg: Any?): Boolean {
        if (arg == null) return !paramType.isPrimitive
        val type = if (paramType.isPrimitive) boxedTypes.getValue(paramType) else paramType
        return type.isInstance(arg)
    }

    fun findMethodBestMatch(clazz: Class<*>, name: String, args: Array<out Any?>): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull { method ->
                method.name == name &&
                    method.parameterTypes.size == args.size &&
                    method.parameterTypes.withIndex().all { (i, type) -> isCompatible(type, args[i]) }
            }?.let { return it.apply { isAccessible = true } }
            current = current.superclass
        }
        clazz.methods.firstOrNull { method ->
            method.name == name &&
                method.parameterTypes.size == args.size &&
                method.parameterTypes.withIndex().all { (i, type) -> isCompatible(type, args[i]) }
        }?.let { return it.apply { isAccessible = true } }
        throw NoSuchMethodError("${clazz.name}#$name(${args.joinToString { it?.javaClass?.name ?: "null" }})")
    }

    fun callMethod(obj: Any, name: String, vararg args: Any?): Any? {
        val method = findMethodBestMatch(obj.javaClass, name, args)
        return try {
            method.invoke(obj, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}
