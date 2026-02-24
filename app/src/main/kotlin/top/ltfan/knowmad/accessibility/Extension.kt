/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2026 LTFan (aka xfqwdsj)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package top.ltfan.knowmad.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import top.ltfan.knowmad.util.Logger

inline fun <R> AccessibilityNodeInfo.use(block: (AccessibilityNodeInfo) -> R): R {
    try {
        return block(this)
    } finally {
        @Suppress("DEPRECATION")
        recycle()
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun AccessibilityNodeInfo.retake(): AccessibilityNodeInfo = use {
    @Suppress("DEPRECATION")
    AccessibilityNodeInfo.obtain(it)
}

inline fun <reified T> Context.requestEnableAccessibilityService() {
    val logger = Logger("requestEnableAccessibilityService")

    val serviceComponent = ComponentName(this, T::class.java)
    val serviceName = serviceComponent.flattenToString()

    val bundle = Bundle().apply {
        putString(":settings:fragment_args_key", serviceName)
        putString("preference_key", serviceName)
        putParcelable("component_name", serviceComponent)
    }

    val putExtras: Intent.() -> Intent = {
        putExtra(
            ":settings:show_fragment",
            "com.android.settings.accessibility.ToggleAccessibilityServicePreferenceFragment",
        )
        putExtra("extra_fragment_arg_key", serviceName)
        putExtra(":settings:fragment_args_key", serviceName)
        putExtra(":settings:show_fragment_args", bundle)
        putExtras(bundle)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val subSettingsIntent = Intent().apply {
        component = ComponentName("com.android.settings", "com.android.settings.SubSettings")
        putExtras()
    }

    val pm = packageManager
    val info = pm.resolveActivity(subSettingsIntent, PackageManager.MATCH_DEFAULT_ONLY)

    if (info != null) {
        try {
            logger.debug { "Trying to open accessibility settings for exact service" }
            startActivity(subSettingsIntent)
            return
        } catch (e: Throwable) {
            logger.error(e) { "Failed to open accessibility settings for exact service, trying generic accessibility settings" }
        }
    }

    val genericSettingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).putExtras()
    startActivity(genericSettingsIntent)
}
