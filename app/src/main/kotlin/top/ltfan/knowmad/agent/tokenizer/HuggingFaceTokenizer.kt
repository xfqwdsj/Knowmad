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

package top.ltfan.knowmad.agent.tokenizer

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.huggingface.tokenizers.TokenizerConfig
import okio.Path
import kotlin.io.path.inputStream

class HuggingFaceTokenizer(
    val tokenizerJson: Path,
    val tokenizerConfigJson: Path? = null,
    val options: Map<String, String> = emptyMap(),
) : Tokenizer {
    private val tokenizer by lazy {
        HuggingFaceTokenizer.newInstance(
            tokenizerJson.toNioPath().inputStream(),
            options,
            tokenizerConfigJson?.toNioPath()?.let { TokenizerConfig.load(it) },
        )
    }

    override fun encode(text: String): Encoding {
        val encoding = tokenizer.encode(text)
        return Encoding(
            ids = encoding.ids.toList(),
            attentionMask = encoding.attentionMask.toList(),
        )
    }

    override fun close() {
        tokenizer.close()
    }
}
