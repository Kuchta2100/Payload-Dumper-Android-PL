package com.rajmani7584.payloaddumper.ui.customviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rajmani7584.payloaddumper.ui.components.AppTheme
import com.rajmani7584.payloaddumper.ui.components.components.IconButton
import com.rajmani7584.payloaddumper.ui.components.components.IconButtonVariant
import com.rajmani7584.payloaddumper.ui.components.components.topbar.TopBar
import com.rajmani7584.payloaddumper.ui.components.components.topbar.TopBarColors
import com.rajmani7584.payloaddumper.ui.components.components.topbar.TopBarScrollBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(title: String, modifier: Modifier = Modifier, scrollBehavior: TopBarScrollBehavior? = null, nav: Boolean = false, onNavClick: () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}) {
    TopBar(
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        colors = TopBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (nav)
                IconButton(variant = IconButtonVariant.Ghost, onClick = onNavClick) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Go Back")
                }
            Text(title, style = AppTheme.typography.h2)
            Spacer(Modifier.weight(1f))
            actions()
        }
    }
}