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

package top.ltfan.knowmad.data.llm

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.llm.LLMProvider

val SupportedLLMProviders = listOf(
    LLMProvider.DeepSeek,
    LLMProvider.OpenAI,
)

fun LLMProviderConfigEntity.toClient(): LLMClient {
    return when (provider) {
        LLMProvider.DeepSeek -> toDeepSeekClient()
        else -> error("Unsupported LLM provider: $provider")
    }
}

fun LLMProviderConfigEntity.toDeepSeekClient() = DeepSeekLLMClient(
    apiKey = apiKey.toByteArray()
        .decodeToString(), // TODO: Implement secure storage for API keys
    settings = DeepSeekClientSettings(
        baseUrl = baseUrl ?: "https://api.deepseek.com",
        chatCompletionsPath = chatCompletionsPath ?: "chat/completions",
        timeoutConfig = ConnectionTimeoutConfig(
            requestTimeoutMillis = requestTimeoutMillis ?: 900_000,
            connectTimeoutMillis = connectTimeoutMillis ?: 60_000,
            socketTimeoutMillis = socketTimeoutMillis ?: 900_000,
        ),
    ),
)
