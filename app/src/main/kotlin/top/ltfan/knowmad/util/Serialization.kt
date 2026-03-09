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

import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.modules.SerializersModule

val Cbor = Cbor {
    serializersModule = SerializersModule {
        llmProvidersPolymorphic()
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Cbor(noinline builderAction: CborBuilder.() -> Unit) =
    Cbor(top.ltfan.knowmad.util.Cbor, builderAction)

val Json = Json {
    serializersModule = SerializersModule {
        llmProvidersPolymorphic()
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Json(noinline builderAction: JsonBuilder.() -> Unit) =
    Json(top.ltfan.knowmad.util.Json, builderAction)
