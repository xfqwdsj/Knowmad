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

package top.ltfan.knowmad.ui.viewmodel

import androidx.navigation3.runtime.NavBackStack
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import top.ltfan.knowmad.application.KnowmadApplication
import top.ltfan.knowmad.ui.page.Route
import top.ltfan.knowmad.ui.page.WizardPage

@Serializable(with = AppViewModelFakeSerializer::class)
class AppViewModel(app: KnowmadApplication) : AndroidViewModel<KnowmadApplication>(app) {
    val backStack = NavBackStack<Route>(WizardPage(this))
}

class AppViewModelFakeSerializer() : KSerializer<AppViewModel> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AppViewModel", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AppViewModel) {
        encoder.encodeString("AppViewModel")
    }

    override fun deserialize(decoder: Decoder): AppViewModel {
        error("AppViewModelFakeSerializer cannot be deserialized")
    }
}
