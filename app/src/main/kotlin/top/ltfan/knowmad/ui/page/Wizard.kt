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

package top.ltfan.knowmad.ui.page

import ai.koog.prompt.llm.LLMProvider
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.filled.Upcoming
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import top.ltfan.knowmad.R
import top.ltfan.knowmad.data.llm.SupportedLLMProviders
import top.ltfan.knowmad.ui.component.StepItem
import top.ltfan.knowmad.ui.component.Stepper
import top.ltfan.knowmad.ui.util.AppWindowInsets
import top.ltfan.knowmad.ui.util.only
import top.ltfan.knowmad.ui.util.plus
import top.ltfan.knowmad.ui.viewmodel.AppViewModel
import top.ltfan.knowmad.util.CryptoManager

@Serializable
class WizardPage(
    val backStack: NavBackStack<WizardSubPage> = NavBackStack(WelcomePage),
) : Page() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    context(contentPadding: PaddingValues) override fun AppViewModel.Content() {
        val backStack = this@WizardPage.backStack
        val currentPage = backStack.last()

        val insets = AppWindowInsets + contentPadding
        val horizontalPadding = insets.only { horizontal }.asPaddingValues()

        Surface {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(insets.only { vertical }),
            ) {
                Spacer(Modifier.height(16.dp))
                Stepper(
                    steps = steps,
                    currentStep = backStack.size - 1,
                    contentPadding = horizontalPadding,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontalPadding)
                        .padding(horizontal = 8.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Column {
                        AnimatedContent(
                            targetState = messageItems,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                        ) { messageItems ->
                            val hasMessages = messageItems.any { it.second }
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp).run {
                                    if (hasMessages) padding(vertical = 8.dp)
                                    else this
                                },
                                verticalArrangement = if (hasMessages) Arrangement.spacedBy(8.dp) else Arrangement.Top,
                            ) {
                                for ((item, show) in messageItems) {
                                    if (show) {
                                        Message(
                                            imageVector = item.icon,
                                            message = stringResource(item.message),
                                        )
                                    }
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ) {
                            NavDisplay(
                                backStack = backStack,
                                modifier = Modifier.fillMaxSize(),
                                transitionSpec = {
                                    ContentTransform(
                                        targetContentEnter = fadeIn() + slideInHorizontally { fullWidth -> fullWidth },
                                        initialContentExit = fadeOut() + slideOutHorizontally { fullWidth -> -fullWidth },
                                    )
                                },
                                popTransitionSpec = {
                                    ContentTransform(
                                        targetContentEnter = fadeIn() + slideInHorizontally { fullWidth -> -fullWidth },
                                        initialContentExit = fadeOut() + slideOutHorizontally { fullWidth -> fullWidth },
                                    )
                                },
                                predictivePopTransitionSpec = {
                                    ContentTransform(
                                        targetContentEnter = fadeIn() + slideInHorizontally { fullWidth -> -fullWidth },
                                        initialContentExit = fadeOut() + slideOutHorizontally { fullWidth -> fullWidth },
                                    )
                                },
                                entryProvider = @Suppress("UNCHECKED_CAST") {
                                    it.navEntry() as NavEntry<WizardSubPage>
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontalPadding),
                ) {
                    Spacer(Modifier.width(16.dp))
                    TextButton(
                        onClick = {},
                    ) {
                        Text(stringResource(R.string.label_skip_for_now))
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            backStack.removeLastOrNull()
                        },
                        enabled = backStack.size > 1,
                    ) {
                        Text(stringResource(R.string.label_back))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            currentPage.nextPage?.invoke(this@WizardPage) // TODO: handle finish
                        },
                        enabled = currentPage.canContinue,
                    ) {
                        Text(stringResource(R.string.label_next))
                    }
                    Spacer(Modifier.width(16.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    @Composable
    fun Message(imageVector: ImageVector, message: String) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    var cryptoInitializationError by mutableStateOf<Boolean>(false)
    var isUsingPlaintext by mutableStateOf<Boolean>(false)

    val messageItems
        inline get() = listOf(
            WizardMessageItem(
                icon = Icons.Default.Error,
                message = R.string.crypto_key_initialization_error_message,
            ) to cryptoInitializationError,
            WizardMessageItem(
                icon = Icons.Default.Warning,
                message = R.string.crypto_use_plaintext_warning,
            ) to isUsingPlaintext,
        )

    var selectedProvider by mutableStateOf<LLMProvider?>(null)

    val steps
        @Composable inline get() = listOf(
            StepItem(stringResource(R.string.setup_wizard_welcome_label)),
            StepItem(stringResource(R.string.setup_wizard_provider_label)),
            StepItem(stringResource(R.string.setup_wizard_model_label)),
            StepItem(stringResource(R.string.setup_wizard_advanced_label)),
            StepItem(stringResource(R.string.setup_wizard_finish_label)),
        )

    val providers = SupportedLLMProviders.associateWith {
        when (it) {
            LLMProvider.DeepSeek -> LLMProviderItem(
                icon = R.drawable.ic_llm_provider_deepseek,
                label = R.string.llm_provider_deepseek_label,
                description = R.string.llm_provider_deepseek_description,
            )

            LLMProvider.OpenAI -> LLMProviderItem(
                icon = R.drawable.ic_llm_provider_openai,
                label = R.string.llm_provider_openai_label,
                description = R.string.llm_provider_openai_description,
            )

            else -> error("Unsupported LLM provider: $it")
        }
    }
}

@Immutable
data class WizardMessageItem(
    val icon: ImageVector,
    @param:StringRes val message: Int,
)

@Serializable
@Immutable
data class LLMProviderItem(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    @param:StringRes val description: Int,
)

@Serializable
private data object WelcomePage : WizardSubPage() {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    context(contentPadding: PaddingValues) override fun AppViewModel.Content() {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(48.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.setup_wizard_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.setup_wizard_welcome_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    override val canContinue = true
    override val nextPage: (wizardPage: WizardPage) -> Unit = { wizardPage ->
        wizardPage.backStack.add(ProviderSetupPage(wizardPage))
    }
}

@Serializable
private class ProviderSetupPage(val wizardPage: WizardPage) : WizardSubPage() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    context(contentPadding: PaddingValues) override fun AppViewModel.Content() {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    Icons.Default.Upcoming,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(48.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.setup_wizard_provider_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.setup_wizard_provider_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(36.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((provider, item) in wizardPage.providers.entries) {
                    Provider(
                        modifier = Modifier
                            .widthIn(max = 360.dp)
                            .fillMaxWidth(),
                        icon = item.icon,
                        label = item.label,
                        description = item.description,
                        selected = wizardPage.selectedProvider == provider,
                        onSelect = {
                            wizardPage.selectedProvider = provider
                        },
                    )
                }
            }
        }
    }

    @Composable
    fun Provider(
        modifier: Modifier = Modifier,
        @DrawableRes icon: Int,
        @StringRes label: Int,
        @StringRes description: Int,
        selected: Boolean,
        onSelect: () -> Unit,
    ) {
        val background by animateColorAsState(
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        )
        Surface(
            modifier = modifier.padding(vertical = 8.dp),
            shape = MaterialTheme.shapes.large,
            color = background,
            onClick = onSelect,
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    AnimatedContent(
                        targetState = selected,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        contentAlignment = Alignment.Center,
                    ) { selected ->
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(36.dp),
                            )
                        } else {
                            Icon(
                                painterResource(icon),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(36.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(description),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    override val canContinue get() = wizardPage.selectedProvider != null
    override val nextPage: (wizardPage: WizardPage) -> Unit = { wizardPage ->
        wizardPage.backStack.add(ModelSetupPage(wizardPage))
    }
}

@Serializable
private class ModelSetupPage(val wizardPage: WizardPage) : WizardSubPage() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    context(contentPadding: PaddingValues) override fun AppViewModel.Content() {
        val mainIconShapeMatrix = remember { Matrix() }
        val mainIconShapeMorph =
            remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Cookie9Sided) }
        val mainIconShapeProgress by animateFloatAsState(
            if (isInitialized) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        )
        val mainIconShapeRotation by animateFloatAsState(
            if (isInitialized) 0f else 135f,
            animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        )
        val mainIconShapeColor by animateColorAsState(
            if (isInitialized) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.drawWithCache {
                    mainIconShapeMatrix.reset()
                    mainIconShapeMatrix.translate(size.width / 2f, size.height / 2f)
                    mainIconShapeMatrix.rotateZ(mainIconShapeRotation)
                    mainIconShapeMatrix.scale(size.width, size.height)
                    mainIconShapeMatrix.translate(-0.5f, -0.5f)

                    val path =
                        mainIconShapeMorph.toPath(progress = mainIconShapeProgress).asComposePath()
                    path.transform(mainIconShapeMatrix)

                    onDrawBehind {
                        drawPath(
                            path = path,
                            color = mainIconShapeColor,
                        )
                    }
                },
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides contentColorFor(mainIconShapeColor),
                ) {
                    AnimatedContent(
                        targetState = isInitialized,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        contentAlignment = Alignment.Center,
                    ) { isInitialized ->
                        Icon(
                            if (isInitialized) Icons.Default.Token else Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(16.dp)
                                .size(48.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            AnimatedContent(
                targetState = isInitialized,
                transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
            ) { isInitialized ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!isInitialized) {
                        ErrorElements()
                        return@AnimatedContent
                    }
                    Text(
                        stringResource(R.string.setup_wizard_model_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.setup_wizard_model_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(36.dp))
                    SecureTextField(
                        state = apiKeyTextFieldState,
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.llm_api_key_label))
                        },
                        leadingIcon = {
                            Icon(
                                if (wizardPage.isUsingPlaintext) Icons.Default.NoEncryption else Icons.Default.EnhancedEncryption,
                                contentDescription = null,
                            )
                        },
                        supportingText = {
                            Row {
                                AnimatedContent(
                                    targetState = wizardPage.isUsingPlaintext,
                                    modifier = Modifier.weight(1f),
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                ) { isUsingPlaintext ->
                                    Text(
                                        stringResource(
                                            if (isUsingPlaintext) R.string.llm_api_key_message_unsecure
                                            else R.string.llm_api_key_message_secure,
                                        ),
                                    )
                                }
                                AnimatedVisibility(
                                    visible = wizardPage.isUsingPlaintext,
                                    enter = fadeIn() + expandHorizontally(
                                        expandFrom = Alignment.Start,
                                        clip = false,
                                    ),
                                    exit = fadeOut() + shrinkHorizontally(
                                        shrinkTowards = Alignment.Start,
                                        clip = false,
                                    ),
                                ) {
                                    Row {
                                        Spacer(Modifier.width(8.dp))
                                        TextButton(
                                            onClick = ::initializeCrypto,
                                        ) {
                                            Text(stringResource(R.string.crypto_key_initialization_error_retry_label))
                                        }
                                    }
                                }
                            }
                        },
                    )
                    
                    Button({ this@ModelSetupPage.isInitialized = false }) {}
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun ErrorElements() {
        Text(
            stringResource(R.string.crypto_key_initialization_error_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.crypto_key_initialization_error_message),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = ::initializeCrypto,
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) {
            Text(stringResource(R.string.crypto_key_initialization_error_retry_label))
        }
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = {
                wizardPage.isUsingPlaintext = true
                isInitialized = true
            },
        ) {
            Text(stringResource(R.string.crypto_use_plaintext_label))
        }
    }

    var isInitialized by mutableStateOf(!wizardPage.cryptoInitializationError)

    @Transient
    val apiKeyTextFieldState = TextFieldState()
    val apiKey inline get() = apiKeyTextFieldState.text.toString()

    init {
        initializeCrypto()
    }

    fun initializeCrypto() {
        CryptoManager.LLMApiKey.generateKey()
        wizardPage.cryptoInitializationError = !CryptoManager.LLMApiKey.isKeyInitialized()
        isInitialized = !wizardPage.cryptoInitializationError
        if (!wizardPage.cryptoInitializationError) {
            wizardPage.isUsingPlaintext = false
        }
    }

    override var canContinue by mutableStateOf(false)
    override val nextPage: (wizardPage: WizardPage) -> Unit = { wizardPage ->
        wizardPage.backStack.add(AdvancedSettingsPage(wizardPage))
    }
}

@Serializable
private class AdvancedSettingsPage(val wizardPage: WizardPage) : WizardSubPage() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    context(contentPadding: PaddingValues) override fun AppViewModel.Content() {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    Icons.Default.Token,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(48.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.setup_wizard_advanced_label),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(24.dp))

        }
    }

    override val canContinue = true
    override val nextPage: (wizardPage: WizardPage) -> Unit = { wizardPage ->
        wizardPage.backStack.add(FinishPage)
    }
}

@Serializable
private data object FinishPage : WizardSubPage() {
    @Composable
    context(contentPadding: PaddingValues) override fun AppViewModel.Content() {

    }

    override val canContinue = true
    override val nextPage: ((wizardPage: WizardPage) -> Unit)? = null
}

@Serializable
sealed class WizardSubPage : SubPage() {
    abstract val canContinue: Boolean
    abstract val nextPage: ((wizardPage: WizardPage) -> Unit)?
}
