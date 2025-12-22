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

package top.ltfan.knowmad.data.wizard

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import top.ltfan.knowmad.data.DataStoreCompanion

@Serializable
data class WizardData(
    val provider: LLMProvider? = null,
    val baseUrl: String? = null,
    val model: LLModel? = null,
) {
    companion object : DataStoreCompanion<WizardData> {
        override val fileName = "wizard_data"
        override val default = WizardData()
    }
}

@Serializable
data class WizardState(
    val data: FirstJoinedData? = null,
) {
    companion object : DataStoreCompanion<WizardState> {
        override val fileName = "wizard_state_data"
        override val default = WizardState()
    }
}

@Serializable
data class FirstJoinedData(
    val instant: Instant,
    val assistantFirstMessage: String? = null,
)
