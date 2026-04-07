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

package top.ltfan.knowmad.modelscope

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelScopeFilesResponse(
    @SerialName("Code")
    val code: Int,
    @SerialName("Message")
    val message: String,
    @SerialName("RequestId")
    val requestId: String,
    @SerialName("Success")
    val success: Boolean,
    @SerialName("Data")
    val data: ModelScopeFilesData?,
)

@Serializable
data class ModelScopeFilesData(
    @SerialName("Files")
    val files: List<ModelScopeFile>,
)

@Serializable
data class ModelScopeFile(
    @SerialName("Mode")
    val mode: String,
    @SerialName("Name")
    val name: String,
    @SerialName("Path")
    val path: String,
    @SerialName("Revision")
    val revision: String,
    @SerialName("Sha256")
    val sha256: String,
    @SerialName("Size")
    val size: Long,
    @SerialName("Type")
    val type: ModelScopeFileType,
)

@Serializable
enum class ModelScopeFileType {
    @SerialName("tree")
    Tree,

    @SerialName("blob")
    Blob,
}
