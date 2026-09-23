package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ProtocolTheme

fun triggerHaptic(context: Context, type: Int = 0) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = when (type) {
                1 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                2 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                3 -> VibrationEffect.createWaveform(longArrayOf(0, 35, 60, 45), -1) // Heartbeat pulse for breath pacer
                4 -> VibrationEffect.createWaveform(longArrayOf(0, 20, 40, 25, 50, 40), -1) // Success celebratory burst
                5 -> VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 70), -1) // Error alert
                else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            when (type) {
                1 -> vibrator.vibrate(25L)
                2 -> vibrator.vibrate(60L)
                3 -> vibrator.vibrate(longArrayOf(0, 30, 60, 30), -1)
                4 -> vibrator.vibrate(longArrayOf(0, 20, 30, 20, 30, 40), -1)
                5 -> vibrator.vibrate(longArrayOf(0, 50, 80, 50), -1)
                else -> vibrator.vibrate(12L)
            }
        }
    } catch (_: Exception) {}
}

@Composable
fun ProtocolPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val palette = ProtocolTheme.palette
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) palette.accent else palette.accent.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = palette.accentForeground),
                enabled = enabled,
                role = Role.Button
            ) {
                triggerHaptic(context, 1)
                onClick()
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = palette.accentForeground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
                modifier = Modifier.weight(1f)
            )
            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}

@Composable
fun ProtocolLogoMark(
    modifier: Modifier = Modifier,
    sizeDp: Int = 26
) {
    val palette = ProtocolTheme.palette
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp / 3).dp))
            .background(palette.accent),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width((sizeDp / 7).coerceAtLeast(3).dp)
                .height((sizeDp * 0.6f).dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.accentForeground)
        )
    }
}
