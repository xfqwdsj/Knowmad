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

package top.ltfan.knowmad.ui.component

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import top.ltfan.knowmad.ui.theme.AppTheme

@Composable
fun Preview(content: @Composable () -> Unit) {
    AppTheme {
        Surface {
            content()
        }
    }
}

@Composable
fun ApplicationPreview(content: @Composable Application.() -> Unit) {
    Preview {
        if (LocalInspectionMode.current) {
            Text("Inspection Mode")
            return@Preview
        }

        val application = LocalContext.current.applicationContext as? Application ?: run {
            Text("No Application Context")
            return@Preview
        }

        application.content()
    }
}
