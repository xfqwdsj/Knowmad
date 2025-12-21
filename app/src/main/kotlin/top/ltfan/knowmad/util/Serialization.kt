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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

open class EmptySerializer<T : Any?> : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor("Empty", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) {}
    override fun deserialize(decoder: Decoder): T {
        error("This serializer does not support deserialization.")
    }
}
