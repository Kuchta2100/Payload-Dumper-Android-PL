package com.rajmani7584.payloaddumper.ui.customviews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier, size: Dp = 30.dp, speed: Long = 200, color: Color = Color.LightGray, activeColor: Color = Color(0xFF1B1D1F), secondActiveColor: Color = Color.Gray) {
    val n = 3
    val active = remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            active.intValue = (active.intValue + 1) % n
            delay(speed)
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.wrapContentHeight()
    ) {
        for (i in 0 until n) {
            Box(
                modifier = Modifier.background(
                    if (active.intValue == i) activeColor else if (i == 1) secondActiveColor else color,
                    shape = CircleShape
                ).size(size)
            )
        }
    }
}
