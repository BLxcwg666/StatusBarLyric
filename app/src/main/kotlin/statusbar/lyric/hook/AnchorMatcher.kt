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

import android.content.res.Resources
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import statusbar.lyric.R
import statusbar.lyric.config.XposedOwnSP.config
import statusbar.lyric.tools.LogTools.log
import statusbar.lyric.tools.ReflectTools
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * Decides which TextView in SystemUI is the lyric anchor (the status bar clock picked on the
 * anchor page). Main thread only, like the view callbacks that drive it.
 */
object AnchorMatcher {
    private const val SIZE_TOLERANCE = 0.5f

    private class Spec(
        val className: String,
        val textViewId: Int,
        val parentClassName: String,
        val parentViewId: Int,
        val textSizePx: Float,
        val textSizeDp: Float,
        val textSizeSp: Float,
        val index: Int,
    ) {
        val isValid: Boolean
            get() = className.isNotEmpty() && parentClassName.isNotEmpty() &&
                textViewId != 0 && parentViewId != 0 && textSizePx != 0f
    }

    private var spec: Spec? = null
    private var loggedInvalid = false

    // Distinct candidate views in discovery order; the configured index picks one of them.
    private val candidates = ArrayList<WeakReference<View>>()

    fun invalidate() {
        spec = null
        loggedInvalid = false
        candidates.clear()
    }

    fun isTargetView(view: View): Boolean {
        val spec = currentSpec() ?: return false
        if (!isCandidate(view, spec)) return false
        return indexOf(view) == spec.index
    }

    /** Walks every SystemUI window for the anchor, for when no onLayout arrives after a status bar rebuild. */
    fun findInWindows(): TextView? {
        val spec = currentSpec() ?: return null
        val found = ArrayList<TextView>()
        rootViews().forEach { collect(it, spec, found) }
        return found.firstOrNull { indexOf(it) == spec.index }
    }

    fun resourceNameOf(view: View): String {
        if (view.id == View.NO_ID) return ""
        return try {
            view.resources.getResourceName(view.id)
        } catch (_: Resources.NotFoundException) {
            ""
        }
    }

    fun pxToDp(view: TextView): Float = view.textSize / view.resources.displayMetrics.density

    fun pxToSp(view: TextView): Float {
        val metrics = view.resources.displayMetrics
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, view.textSize, metrics)
        } else {
            @Suppress("DEPRECATION")
            view.textSize / metrics.scaledDensity
        }
    }

    private fun currentSpec(): Spec? {
        val spec = spec ?: buildSpec().also { spec = it }
        if (!spec.isValid) {
            if (!loggedInvalid) {
                loggedInvalid = true
                HookEnv.moduleRes.getString(R.string.load_class_empty).log()
            }
            return null
        }
        return spec
    }

    private fun buildSpec(): Spec {
        val resources = HookEnv.appContext.resources
        val spec = Spec(
            className = config.textViewClassName,
            textViewId = resolveId(resources, config.textViewResName, config.textViewId),
            parentClassName = config.parentViewClassName,
            parentViewId = resolveId(resources, config.parentViewResName, config.parentViewId),
            textSizePx = config.textSize,
            textSizeDp = config.textSizeDp,
            textSizeSp = config.textSizeSp,
            index = config.index,
        )
        "Anchor spec: id=${spec.textViewId} (saved ${config.textViewId}, ${config.textViewResName.ifEmpty { "no name" }}), parent=${spec.parentViewId} (saved ${config.parentViewId}, ${config.parentViewResName.ifEmpty { "no name" }}), size=${spec.textSizePx}px/${spec.textSizeDp}dp/${spec.textSizeSp}sp, index=${spec.index}".log()
        return spec
    }

    // Prefer the resource name: numeric IDs are renumbered whenever SystemUI itself is updated.
    private fun resolveId(resources: Resources, resName: String, savedId: Int): Int {
        if (resName.isEmpty()) return savedId
        @Suppress("DiscouragedApi")
        val id = resources.getIdentifier(resName, null, null)
        return if (id != 0) id else savedId
    }

    private fun isCandidate(view: View, spec: Spec): Boolean {
        if (view.id != spec.textViewId) return false
        if (view !is TextView || view.javaClass.name != spec.className) return false
        val parent = view.parent as? LinearLayout ?: return false
        if (parent.id != spec.parentViewId || parent.javaClass.name != spec.parentClassName) return false
        return sizeMatches(view, spec)
    }

    private fun sizeMatches(view: TextView, spec: Spec): Boolean {
        if (abs(view.textSize - spec.textSizePx) < SIZE_TOLERANCE) return true
        if (spec.textSizeDp > 0f && abs(pxToDp(view) - spec.textSizeDp) < SIZE_TOLERANCE) return true
        if (spec.textSizeSp > 0f && abs(pxToSp(view) - spec.textSizeSp) < SIZE_TOLERANCE) return true
        return false
    }

    private fun indexOf(view: View): Int {
        candidates.removeAll { ref ->
            val candidate = ref.get()
            candidate == null || (candidate !== view && !candidate.isAttachedToWindow)
        }
        val position = candidates.indexOfFirst { it.get() === view }
        if (position >= 0) return position
        candidates.add(WeakReference(view))
        return candidates.size - 1
    }

    private fun collect(view: View, spec: Spec, out: MutableList<TextView>) {
        if (view is TextView && view.isAttachedToWindow && isCandidate(view, spec)) out.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collect(view.getChildAt(i), spec, out)
        }
    }

    private fun rootViews(): List<View> {
        return try {
            val globalClass = Class.forName("android.view.WindowManagerGlobal")
            val global = globalClass.getMethod("getInstance").invoke(null) ?: return emptyList()
            @Suppress("UNCHECKED_CAST")
            val views = ReflectTools.findField(globalClass, "mViews").get(global) as List<View>
            views.toList()
        } catch (t: Throwable) {
            t.log()
            emptyList()
        }
    }
}
