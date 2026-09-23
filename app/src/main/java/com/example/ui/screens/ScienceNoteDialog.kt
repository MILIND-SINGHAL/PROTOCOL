package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.ProtocolPrimaryButton
import com.example.ui.theme.ProtocolTheme

@Composable
fun ScienceNoteDialog(
    onDismiss: () -> Unit
) {
    val palette = ProtocolTheme.palette

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(palette.surface)
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Sheet handle
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.border)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SCIENCE NOTE",
                    color = palette.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.8.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Magnesium L-Threonate & Sleep Support",
                    color = palette.foreground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Magnesium is an essential mineral involved in over 300 enzymatic reactions in the human body, including the regulation of GABAergic neurotransmission and neuromuscular relaxation.\n\nFormulations such as Magnesium L-threonate and Magnesium Bisglycinate have been studied for their cognitive and relaxation properties. Research suggests maintaining optimal magnesium levels helps promote the physical calm and parasympathetic tone associated with restorative sleep.\n\nNote: For informational purposes only. Consult your qualified physician before introducing dietary supplements.",
                    color = palette.mutedForeground,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                ProtocolPrimaryButton(
                    text = "Done",
                    onClick = onDismiss,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done",
                            tint = palette.accentForeground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}
