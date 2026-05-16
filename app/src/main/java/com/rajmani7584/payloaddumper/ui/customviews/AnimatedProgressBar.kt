package com.rajmani7584.payloaddumper.ui.customviews

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedProgressBar(
    modifier: Modifier = Modifier,
    progress: Float = 100f,
    backgroundColor: Color = Color(0xFFD7FDD5),
    highlightColor: Color = Color(0xFFACF5A2)
) {
    val highlightWidthFraction = 0.2f // 20% of the progress bar width
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val animatedOffset = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ), label = ""
    )
    Box(
        modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.div(100))
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.div(100))
                .clip(RoundedCornerShape(8.dp))
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val totalWidth = size.width
                val highlightWidth = totalWidth * highlightWidthFraction
                val highlightStart =
                    animatedOffset.value * (totalWidth + highlightWidth) - highlightWidth

                drawRect(
                    color = highlightColor.copy(.5f),
                    topLeft = Offset(highlightStart, 0f),
                    size = Size(highlightWidth, size.height)
                )
            }
        }
    }
}