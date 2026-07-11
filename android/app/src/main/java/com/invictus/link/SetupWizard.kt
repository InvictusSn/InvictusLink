package com.invictus.link

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SetupWizardOverlay(
    step: Int,
    bridgeUrl: String,
    onBridgeUrlChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
) {
    val totalSteps = 3
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InvictusBrand.NavyDeep)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(InvictusDimens.pageHorizontal),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(InvictusDimens.sectionGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    InvictusTextButton(onClick = onBack) { Text("Back") }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                InvictusTextButton(onClick = onDismiss) { Text("Skip") }
            }

            if (step == 0) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Invictus Link",
                        style = MaterialTheme.typography.headlineLarge,
                        color = InvictusBrand.White,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your universe, your way",
                        style = MaterialTheme.typography.titleMedium,
                        color = InvictusBrand.Muted,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Connect your phone to the Cursor agent on your PC — privately, over your own network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InvictusBrand.Muted,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(InvictusDimens.itemGap),
                ) {
                    Text(
                        "Step $step of $totalSteps",
                        style = MaterialTheme.typography.labelMedium,
                        color = InvictusBrand.Accent,
                    )
                    when (step) {
                        1 -> {
                            Text(
                                "Install WireGuard and connect your tunnel",
                                style = MaterialTheme.typography.titleLarge,
                                color = InvictusBrand.White,
                            )
                            Text(
                                "Import your phone profile from Invictus Networks (QR or .conf). If you use Tailscale instead, install and connect it first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InvictusBrand.Muted,
                            )
                        }
                        2 -> {
                            Text(
                                "Enter your PC bridge URL",
                                style = MaterialTheme.typography.titleLarge,
                                color = InvictusBrand.White,
                            )
                            Text(
                                "Example: http://10.66.66.21:3003 — your PC's VPN IP on port 3003.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InvictusBrand.Muted,
                            )
                            InvictusTextField(
                                value = bridgeUrl,
                                onValueChange = onBridgeUrlChange,
                                label = "PC bridge URL",
                                placeholder = "http://10.66.66.21:3003",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            )
                        }
                        else -> {
                            Text(
                                "Enter your pairing code",
                                style = MaterialTheme.typography.titleLarge,
                                color = InvictusBrand.White,
                            )
                            Text(
                                "Find BRIDGE_TOKEN in your bridge .env on your PC.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InvictusBrand.Muted,
                            )
                            InvictusTextField(
                                value = pairingCode,
                                onValueChange = onPairingCodeChange,
                                label = "Pairing code",
                                visualTransformation = PasswordVisualTransformation(),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(totalSteps) { index ->
                    val active = (step == 0 && index == 0) || (step > 0 && index == step - 1)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (active) InvictusBrand.Accent
                                else InvictusBrand.HairlineStrong,
                            ),
                    )
                }
            }

            InvictusPrimaryButton(
                onClick = if (step >= totalSteps) onFinish else onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        step == 0 -> "Get started"
                        step >= totalSteps -> "Finish setup"
                        else -> "Continue"
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** @deprecated Use [SetupWizardOverlay] — kept as alias for compatibility. */
@Composable
fun SetupWizardDialog(
    step: Int,
    bridgeUrl: String,
    onBridgeUrlChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
) = SetupWizardOverlay(
    step = step,
    bridgeUrl = bridgeUrl,
    onBridgeUrlChange = onBridgeUrlChange,
    pairingCode = pairingCode,
    onPairingCodeChange = onPairingCodeChange,
    onNext = onNext,
    onBack = onBack,
    onFinish = onFinish,
    onDismiss = onDismiss,
)
