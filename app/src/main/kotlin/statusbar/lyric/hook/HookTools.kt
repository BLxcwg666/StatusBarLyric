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

import io.github.libxposed.api.XposedInterface
import statusbar.lyric.tools.LogTools.log
import statusbar.lyric.tools.ReflectTools
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/*
 * A thin before/after layer on top of the libxposed API 101 interceptor chain. It keeps the
 * call-site shape of the EzXHelper 2.x API the hooks were originally written against.
 */

class HookParam internal constructor(private val chain: XposedInterface.Chain) {
    val method: Executable get() = chain.executable
    val thisObject: Any get() = chain.thisObject
    val args: Array<Any?> = chain.args.toTypedArray()

    internal var returnEarly = false
        private set

    private var resultValue: Any? = null
    private var throwableValue: Throwable? = null

    // Same semantics as the legacy MethodHookParam: setting it in `before` skips the original call.
    var result: Any?
        get() = resultValue
        set(value) {
            resultValue = value
            throwableValue = null
            returnEarly = true
        }

    var throwable: Throwable?
        get() = throwableValue
        set(value) {
            throwableValue = value
            resultValue = null
            returnEarly = true
        }
}

class HookFactory internal constructor(private val name: String) : XposedInterface.Hooker {
    private var beforeCallback: ((HookParam) -> Unit)? = null
    private var afterCallback: ((HookParam) -> Unit)? = null

    fun before(callback: (HookParam) -> Unit) {
        beforeCallback = callback
    }

    fun after(callback: (HookParam) -> Unit) {
        afterCallback = callback
    }

    fun replace(callback: (HookParam) -> Any?) {
        beforeCallback = { it.result = callback(it) }
    }

    fun returnConstant(value: Any?) {
        beforeCallback = { it.result = value }
    }

    override fun intercept(chain: XposedInterface.Chain): Any? {
        val param = HookParam(chain)
        beforeCallback?.let { callback ->
            try {
                callback(param)
            } catch (t: Throwable) {
                "Hook before error: $name".log()
                t.log()
            }
        }
        if (!param.returnEarly) {
            try {
                param.result = chain.proceed(param.args)
            } catch (t: Throwable) {
                param.throwable = t
            }
        }
        afterCallback?.let { callback ->
            try {
                callback(param)
            } catch (t: Throwable) {
                "Hook after error: $name".log()
                t.log()
            }
        }
        param.throwable?.let { throw it }
        return param.result
    }
}

fun Executable.createHook(block: HookFactory.() -> Unit): XposedInterface.HookHandle {
    val factory = HookFactory("${declaringClass.name}#$name").apply(block)
    return HookEnv.module.hook(this).intercept(factory)
}

class MethodFinder internal constructor(private var methods: Sequence<Method>) {
    fun filterByName(name: String) = apply { methods = methods.filter { it.name == name } }
    fun filterNonAbstract() = apply { methods = methods.filter { !Modifier.isAbstract(it.modifiers) } }
    fun filterFinal() = apply { methods = methods.filter { Modifier.isFinal(it.modifiers) } }
    fun first(): Method = methods.first()
    fun single(): Method = methods.single()
    fun singleOrNull(): Method? = methods.singleOrNull()
}

class ConstructorFinder internal constructor(private val constructors: Sequence<Constructor<*>>) {
    fun first(): Constructor<*> = constructors.first()
    fun single(): Constructor<*> = constructors.single()
    fun singleOrNull(): Constructor<*>? = constructors.singleOrNull()
}

fun Class<*>.methodFinder() = MethodFinder(declaredMethods.asSequence())

fun Class<*>.constructorFinder() = ConstructorFinder(declaredConstructors.asSequence())

fun loadClassOrNull(name: String, classLoader: ClassLoader = HookEnv.classLoader): Class<*>? {
    if (name.isEmpty()) return null
    return try {
        Class.forName(name, false, classLoader)
    } catch (_: Throwable) {
        null
    }
}

class ObjectHelper(private val obj: Any) {
    @Suppress("UNCHECKED_CAST")
    fun <T> getObjectOrNullAs(name: String): T? {
        return try {
            ReflectTools.findFieldOrNull(obj.javaClass, name)?.get(obj) as T?
        } catch (_: Throwable) {
            null
        }
    }
}

fun Any.objectHelper() = ObjectHelper(this)

inline fun <T> Any.objectHelper(block: ObjectHelper.() -> T): T = ObjectHelper(this).block()
