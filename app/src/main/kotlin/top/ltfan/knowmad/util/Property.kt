/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025 LTFan (aka xfqwdsj)
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

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

inline fun <T, R, P : ReadWriteProperty<Any?, T>> P.transform(
    crossinline transformIn: T.() -> R,
    crossinline transformOut: T.(R) -> T,
): ReadWriteProperty<Any?, R> = object : ReadWriteProperty<Any?, R> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): R {
        return this@transform.getValue(thisRef, property).transformIn()
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: R) {
        this@transform.setValue(
            thisRef,
            property,
            this@transform.getValue(thisRef, property).transformOut(value),
        )
    }
}
