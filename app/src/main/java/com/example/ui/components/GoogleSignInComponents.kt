package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ProtocolTheme

/**
 * Authentic 4-color Google "G" logo rendered via vector paths
 */
@Composable
fun GoogleLogoIcon(
    sizeDp: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    // Official Google Brand Colors
    val blue = Color(0xFF4285F4)
    val red = Color(0xFFEA4335)
    val yellow = Color(0xFFFBBC05)
    val green = Color(0xFF34A853)

    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val strokeWidth = w * 0.22f
        val radius = (w - strokeWidth) / 2f

        // Top arc (Red)
        val redPath = Path().apply {
            arcTo(
                rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                startAngleDegrees = 200f,
                sweepAngleDegrees = 100f,
                forceMoveTo = true
            )
        }
        drawPath(redPath, red, style = Stroke(width = strokeWidth))

        // Left arc (Yellow)
        val yellowPath = Path().apply {
            arcTo(
                rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                startAngleDegrees = 120f,
                sweepAngleDegrees = 85f,
                forceMoveTo = true
            )
        }
        drawPath(yellowPath, yellow, style = Stroke(width = strokeWidth))

        // Bottom arc (Green)
        val greenPath = Path().apply {
            arcTo(
                rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                startAngleDegrees = 20f,
                sweepAngleDegrees = 105f,
                forceMoveTo = true
            )
        }
        drawPath(greenPath, green, style = Stroke(width = strokeWidth))

        // Blue horizontal bar and right arc
        val blueArc = Path().apply {
            arcTo(
                rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                startAngleDegrees = 300f,
                sweepAngleDegrees = 70f,
                forceMoveTo = true
            )
        }
        drawPath(blueArc, blue, style = Stroke(width = strokeWidth))

        // Horizontal blue stem
        drawLine(
            color = blue,
            start = Offset(cx - strokeWidth * 0.15f, cy),
            end = Offset(cx + radius + strokeWidth * 0.45f, cy),
            strokeWidth = strokeWidth
        )
    }
}

/**
 * Premium Google Sign-In Button compliant with Google Identity Guidelines
 */
@Composable
fun GoogleSignInButton(
    text: String = "Continue with Google",
    subtitle: String = "Fast & secure. Google handles authentication.",
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ProtocolTheme.palette

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surfaceRaised)
            .border(1.2.dp, palette.border, RoundedCornerShape(16.dp))
            .clickable(
                enabled = !isLoading,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .border(1.dp, palette.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF4285F4),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    GoogleLogoIcon(sizeDp = 22.dp)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    color = palette.foreground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = palette.mutedForeground,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF4285F4).copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "OAuth2",
                    color = Color(0xFF4285F4),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

