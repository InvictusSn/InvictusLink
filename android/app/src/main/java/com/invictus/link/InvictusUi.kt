package com.invictus.link

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object InvictusDimens {
    val pageHorizontal = 24.dp
    val pageVertical = 20.dp
    val cardRadius = 16.dp
    val inputRadius = 16.dp
    val chipRadius = 12.dp
    val sectionGap = 20.dp
    val itemGap = 14.dp
    val cardPadding = 16.dp
}

enum class StatusTone { Neutral, Success, Warning, Error, Active }

@Composable
fun Modifier.invictusScreenPadding(): Modifier = padding(
    horizontal = InvictusDimens.pageHorizontal,
    vertical = InvictusDimens.pageVertical,
)

fun Modifier.invictusCardSurface(
    background: Color = InvictusBrand.NavyElevated,
    borderColor: Color = InvictusBrand.Hairline,
): Modifier = this
    .clip(RoundedCornerShape(InvictusDimens.cardRadius))
    .background(background)
    .border(1.dp, borderColor, RoundedCornerShape(InvictusDimens.cardRadius))

fun performConfirmHaptic(view: android.view.View) {
    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
}

fun performTapHaptic(view: android.view.View) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

@Composable
fun InvictusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = Modifier
        .fillMaxWidth()
        .invictusCardSurface()
    val cardModifier = if (onClick != null) {
        base.clickable(onClick = onClick)
    } else {
        base
    }
    Column(
        modifier = cardModifier.then(modifier).padding(InvictusDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(InvictusDimens.itemGap),
        content = content,
    )
}

@Composable
fun InvictusSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = InvictusBrand.White)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InvictusBrand.Muted)
        }
    }
}

@Composable
fun InvictusStatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    showDot: Boolean = true,
) {
    val (bg, fg, dot) = when (tone) {
        StatusTone.Success -> Triple(
            InvictusBrand.Success.copy(alpha = 0.12f),
            InvictusBrand.Success,
            InvictusBrand.Success,
        )
        StatusTone.Warning -> Triple(
            InvictusBrand.Warning.copy(alpha = 0.12f),
            InvictusBrand.Warning,
            InvictusBrand.Warning,
        )
        StatusTone.Error -> Triple(
            InvictusBrand.Error.copy(alpha = 0.12f),
            InvictusBrand.Error,
            InvictusBrand.Error,
        )
        StatusTone.Active -> Triple(
            InvictusBrand.Accent.copy(alpha = 0.16f),
            InvictusBrand.Accent,
            InvictusBrand.Accent,
        )
        StatusTone.Neutral -> Triple(
            InvictusBrand.NavyElevated,
            InvictusBrand.Muted,
            InvictusBrand.Muted,
        )
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(InvictusDimens.chipRadius))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(InvictusDimens.chipRadius))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
fun InvictusChecklistRow(label: String, done: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) InvictusBrand.Success else InvictusBrand.Muted.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) InvictusBrand.White else InvictusBrand.Muted,
        )
    }
}

@Composable
fun InvictusTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    transparentBackground: Boolean = false,
) {
    val containerColor = if (transparentBackground) Color.Transparent else InvictusBrand.NavySurface
    val borderColor = if (transparentBackground) Color.Transparent else InvictusBrand.HairlineStrong
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!transparentBackground) {
                    Modifier.invictusCardSurface(
                        background = containerColor,
                        borderColor = borderColor,
                    )
                } else {
                    Modifier
                },
            ),
        label = label?.let { { Text(it, color = InvictusBrand.Muted) } },
        placeholder = placeholder?.let { { Text(it, color = InvictusBrand.Muted) } },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(InvictusDimens.inputRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            cursorColor = InvictusBrand.Accent,
            focusedTextColor = InvictusBrand.White,
            unfocusedTextColor = InvictusBrand.White,
            disabledTextColor = InvictusBrand.Muted,
            focusedLabelColor = InvictusBrand.Muted,
            unfocusedLabelColor = InvictusBrand.Muted,
        ),
    )
}

@Composable
fun InvictusPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(stiffness = 800f),
        label = "buttonScale",
    )

    Button(
        onClick = {
            if (enabled) performConfirmHaptic(view)
            onClick()
        },
        modifier = modifier
            .scale(scale)
            .height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(InvictusDimens.cardRadius),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
        ),
        interactionSource = interactionSource,
        colors = invictusButtonColors(),
        content = content,
    )
}

@Composable
fun InvictusSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(InvictusDimens.cardRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, InvictusBrand.HairlineStrong),
        colors = invictusOutlinedButtonColors(),
        content = content,
    )
}

@Composable
fun InvictusTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@Composable
fun InvictusSendingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sendingPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sendingAlpha",
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(InvictusBrand.Accent.copy(alpha = alpha)),
        )
        Text(
            "Agent working",
            style = MaterialTheme.typography.labelSmall,
            color = InvictusBrand.Accent.copy(alpha = 0.9f),
        )
    }
}

@Composable
fun InvictusSkeletonBlock(
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(alpha)
            .clip(RoundedCornerShape(InvictusDimens.cardRadius))
            .background(InvictusBrand.NavyElevated)
            .border(1.dp, InvictusBrand.Hairline, RoundedCornerShape(InvictusDimens.cardRadius)),
    )
}

@Composable
fun InvictusBottomBar(
    currentTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    pendingCount: Int,
) {
    val view = LocalView.current

    Surface(
        color = InvictusBrand.NavySurface,
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTab.entries.forEach { tab ->
                val selected = currentTab == tab
                val badgeCount = if (tab == BottomTab.Activity) pendingCount else 0

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(InvictusDimens.cardRadius))
                        .clickable {
                            performTapHaptic(view)
                            onTabSelected(tab)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) InvictusBrand.Accent.copy(alpha = 0.2f)
                                else Color.Transparent,
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (badgeCount > 0) {
                            BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) InvictusBrand.White else InvictusBrand.Muted,
                                )
                            }
                        } else {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) InvictusBrand.White else InvictusBrand.Muted,
                            )
                        }
                    }
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                                .width(20.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(InvictusBrand.Accent),
                        )
                    }
                }
            }
        }
    }
}
