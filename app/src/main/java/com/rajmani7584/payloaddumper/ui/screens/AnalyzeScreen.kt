package com.rajmani7584.payloaddumper.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rajmani7584.payloaddumper.ui.components.components.Scaffold
import com.rajmani7584.payloaddumper.ui.customviews.ScreenTopBar


@Composable
fun AnalyzeScreen() {

    Scaffold(topBar = { ScreenTopBar(title = "Analyzer") }) { innerPadding ->
        Box(Modifier.padding(top = innerPadding.calculateTopPadding()).fillMaxSize()) {
            Text("Analyzer - coming soon...")
        }
    }
}