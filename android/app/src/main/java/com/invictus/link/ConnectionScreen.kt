package com.invictus.link

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun ConnectionScreen(
    diagnostics: ConnectionDiagnostics,
    bridgeBaseUrl: String,
    onBridgeUrlChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    isPaired: Boolean,
    pairingStatus: String,
    pairingInProgress: Boolean,
    onTestConnection: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenTailscale: () -> Unit,
    bridgeHost: String,
) {
    val statusTone = when {
        isPaired && diagnostics.isReady -> StatusTone.Success
        pairingInProgress -> StatusTone.Active
        diagnostics.bridgeReachable -> StatusTone.Warning
        else -> StatusTone.Neutral
    }
    val statusLabel = when {
        isPaired && diagnostics.isReady -> "Connected to your PC"
        isPaired -> "Paired — checking bridge"
        pairingInProgress -> "Connecting…"
        else -> "Not connected"
    }
    var scanHint by remember { mutableStateOf("") }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents?.trim()
        if (!contents.isNullOrBlank()) {
            if (contents.startsWith("http://") || contents.startsWith("https://")) {
                onBridgeUrlChange(contents.trimEnd('/'))
                scanHint = "Bridge URL filled from QR"
            } else {
                scanHint = "That QR doesn't look like a bridge URL"
            }
        }
    }
    val launchScan = launchScan@{
        runCatching {
            scanLauncher.launch(
                ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt("Point at the QR on your PC screen")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                    setCaptureActivity(LinkQrCaptureActivity::class.java)
                },
            )
        }.onFailure { scanHint = "Could not open the scanner" }
        return@launchScan
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchScan() else scanHint = "Camera permission is needed to scan"
    }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .invictusScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(InvictusDimens.sectionGap),
    ) {
        InvictusSectionHeader(
            title = "Connection",
            subtitle = "Secure link between your phone and PC bridge",
        )

        ConnectionHeroCard(
            statusTone = statusTone,
            statusLabel = statusLabel,
            statusMessage = diagnostics.statusMessage,
            diagnostics = diagnostics,
            isPaired = isPaired,
            pairingInProgress = pairingInProgress,
            onTestConnection = onTestConnection,
            onOpenTailscale = onOpenTailscale,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InvictusTextField(
                value = bridgeBaseUrl,
                onValueChange = onBridgeUrlChange,
                label = "Bridge URL",
                placeholder = "http://10.66.66.21:3003",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    IconButton(onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            launchScan()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Icon(
                            Icons.Outlined.QrCodeScanner,
                            contentDescription = "Scan QR code from PC",
                            tint = InvictusBrand.Accent,
                        )
                    }
                },
            )
            Text(
                if (scanHint.isNotBlank()) scanHint
                else "Use your PC's VPN IP, or tap the QR icon to scan from your PC screen.",
                style = MaterialTheme.typography.bodySmall,
                color = if (scanHint.isNotBlank()) InvictusBrand.Accent else InvictusBrand.Muted,
            )
        }

        if (isPaired) {
            InvictusTextField(
                value = "••••••••••••",
                onValueChange = {},
                label = "Pairing code",
                readOnly = true,
                enabled = false,
                trailingIcon = {
                    Icon(Icons.Default.CheckCircle, null, tint = InvictusBrand.Success)
                },
            )
            if (bridgeHost.isNotBlank()) {
                Text(
                    "Reachable at $bridgeHost",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Success,
                )
            }
            InvictusSecondaryButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Disconnect")
            }
        } else {
            InvictusTextField(
                value = pairingCode,
                onValueChange = onPairingCodeChange,
                label = "Pairing code",
                visualTransformation = PasswordVisualTransformation(),
            )
            InvictusPrimaryButton(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !pairingInProgress && pairingCode.isNotBlank() && bridgeBaseUrl.isNotBlank(),
            ) {
                Text(if (pairingInProgress) "Connecting…" else "Connect")
            }
        }

        if (pairingStatus.isNotBlank()) {
            Text(
                pairingStatus,
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.Muted,
            )
        }
    }
}

/** Single hero status card: big dot + status + expandable checklist + actions. */
@Composable
private fun ConnectionHeroCard(
    statusTone: StatusTone,
    statusLabel: String,
    statusMessage: String,
    diagnostics: ConnectionDiagnostics,
    isPaired: Boolean,
    pairingInProgress: Boolean,
    onTestConnection: () -> Unit,
    onOpenTailscale: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val view = LocalView.current
    val dotColor = when (statusTone) {
        StatusTone.Success -> InvictusBrand.Success
        StatusTone.Warning -> InvictusBrand.Warning
        StatusTone.Active -> InvictusBrand.Accent
        StatusTone.Error -> InvictusBrand.Error
        StatusTone.Neutral -> InvictusBrand.Muted
    }
    val pulse = rememberInfiniteTransition(label = "connPulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "connPulseAlpha",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "connChevron",
    )

    InvictusCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    performTapHaptic(view)
                    expanded = !expanded
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = if (pairingInProgress) pulseAlpha else 1f)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = InvictusBrand.White,
                )
                Text(
                    statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Hide checklist" else "Show checklist",
                tint = InvictusBrand.Muted,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HorizontalDivider(color = InvictusBrand.Hairline)
                InvictusChecklistRow(
                    label = "VPN active",
                    done = when {
                        diagnostics.usesInvictusVpnAddress || diagnostics.usesTailscaleAddress ->
                            diagnostics.tailscaleVpnActive
                        else -> true
                    },
                )
                InvictusChecklistRow("Bridge reachable", diagnostics.bridgeReachable)
                InvictusChecklistRow("Paired with PC", isPaired)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InvictusSecondaryButton(
                onClick = onTestConnection,
                modifier = Modifier.weight(1f),
            ) {
                Text("Test connection")
            }
            if (diagnostics.showOpenTailscale) {
                InvictusSecondaryButton(
                    onClick = onOpenTailscale,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Open Tailscale")
                }
            }
        }
    }
}

@Composable
fun ConnectionChecklist(diagnostics: ConnectionDiagnostics, isPaired: Boolean) {
    InvictusCard {
        Text("Connection checklist", style = MaterialTheme.typography.titleSmall, color = InvictusBrand.White)
        InvictusChecklistRow(
            label = "VPN active",
            done = when {
                diagnostics.usesInvictusVpnAddress || diagnostics.usesTailscaleAddress ->
                    diagnostics.tailscaleVpnActive
                else -> true
            },
        )
        InvictusChecklistRow("Bridge reachable", diagnostics.bridgeReachable)
        InvictusChecklistRow("Paired with PC", isPaired)
    }
}
