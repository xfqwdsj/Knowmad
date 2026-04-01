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

import ai.koog.prompt.llm.AlibabaLLMProvider
import ai.koog.prompt.llm.DeepSeekLLMProvider
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.OpenAILLMProvider
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModuleBuilder
import top.ltfan.knowmad.data.llm.SupportedLLMProviders
import top.ltfan.knowmad.model.UnknownLLMProvider

fun SerializersModuleBuilder.llmProvidersPolymorphic() {
    for (info in SupportedLLMProviders.values) {
        with(info) {
            polymorphic()
        }
    }

    // TODO: remove this migration
    polymorphicDefaultDeserializer(LLMProvider::class) { className ->
        // DeepSeek
        if (className == "ai.koog.prompt.llm.LLMProvider.DeepSeek") {
            return@polymorphicDefaultDeserializer object :
                DeserializationStrategy<DeepSeekLLMProvider> {
                override val descriptor =
                    buildClassSerialDescriptor("ai.koog.prompt.llm.LLMProvider.DeepSeek")

                override fun deserialize(decoder: Decoder) = DeepSeekLLMProvider().also {
                    decoder.beginStructure(descriptor).apply {
                        decodeElementIndex(descriptor)
                        endStructure(descriptor)
                    }
                }
            }
        }
        // OpenAI
        if (className == "ai.koog.prompt.llm.LLMProvider.OpenAI") {
            return@polymorphicDefaultDeserializer object :
                DeserializationStrategy<OpenAILLMProvider> {
                override val descriptor =
                    buildClassSerialDescriptor("ai.koog.prompt.llm.LLMProvider.OpenAI")

                override fun deserialize(decoder: Decoder) = OpenAILLMProvider().also {
                    decoder.beginStructure(descriptor).apply {
                        decodeElementIndex(descriptor)
                        endStructure(descriptor)
                    }
                }
            }
        }
        // Alibaba
        if (className == "ai.koog.prompt.llm.LLMProvider.Alibaba") {
            return@polymorphicDefaultDeserializer object :
                DeserializationStrategy<AlibabaLLMProvider> {
                override val descriptor =
                    buildClassSerialDescriptor("ai.koog.prompt.llm.LLMProvider.Alibaba")

                override fun deserialize(decoder: Decoder) = AlibabaLLMProvider().also {
                    decoder.beginStructure(descriptor).apply {
                        decodeElementIndex(descriptor)
                        endStructure(descriptor)
                    }
                }
            }
        }

        null
    }

    polymorphic(
        baseClass = LLMProvider::class,
        actualClass = UnknownLLMProvider::class,
        actualSerializer = UnknownLLMProvider.serializer(),
    )
}
