/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025-2026 LTFan (aka xfqwdsj)
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

package top.ltfan.knowmad.util

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
sealed interface Resource {
    @Immutable
    sealed interface String {
        @Immutable
        data class Original(val value: kotlin.String) : String {
            override fun get(resources: Resources) = value
        }

        @Immutable
        data class Id(
            @param:StringRes val id: Int,
            val formatArgs: Array<out Any>? = null,
        ) : String {
            override fun get(resources: Resources) = if (formatArgs != null) {
                resources.getString(id, *formatArgs)
            } else {
                resources.getString(id)
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Id

                if (id != other.id) return false
                if (!formatArgs.contentEquals(other.formatArgs)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = id
                result = 31 * result + (formatArgs?.contentHashCode() ?: 0)
                return result
            }
        }

        fun get(resources: Resources): kotlin.String
    }
}

fun String.asResource(): Resource.String = Resource.String.Original(this)
fun @receiver:StringRes Int.asStringRes(
    vararg formatArgs: Any,
): Resource.String = Resource.String.Id(this, formatArgs.takeIf { it.isNotEmpty() })
