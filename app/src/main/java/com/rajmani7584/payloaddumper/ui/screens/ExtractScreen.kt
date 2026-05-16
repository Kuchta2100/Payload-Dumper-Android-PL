package com.rajmani7584.payloaddumper.ui.screens

import android.content.ClipData
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.protobuf.ByteString
import com.rajmani7584.payloaddumper.MainActivity
import com.rajmani7584.payloaddumper.engine.chromeos_update_engine.UpdateMetadata
import com.rajmani7584.payloaddumper.model.DataModel
import com.rajmani7584.payloaddumper.model.Utils
import com.rajmani7584.payloaddumper.ui.components.AppTheme
import com.rajmani7584.payloaddumper.ui.components.components.Button
import com.rajmani7584.payloaddumper.ui.components.components.ButtonVariant
import com.rajmani7584.payloaddumper.ui.components.components.ModalBottomSheet
import com.rajmani7584.payloaddumper.ui.components.components.Scaffold
import com.rajmani7584.payloaddumper.ui.components.components.Text
import com.rajmani7584.payloaddumper.ui.components.components.topbar.TopBarDefaults
import com.rajmani7584.payloaddumper.ui.customviews.ScreenTopBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ExtractScreen(appNavController: NavHostController, homeNavController: NavHostController) {

    val dataViewModel: DataModel = viewModel(LocalActivity.current as MainActivity)
    val payload by dataViewModel.payload

    val listState = rememberLazyListState()
    val scrollBehavior = TopBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(topBar = {
        ScreenTopBar(
            title = "payload name...",
            nav = true,
            onNavClick = { homeNavController.popBackStack() },
            scrollBehavior = scrollBehavior
        )
    }) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (payload == null) {
                Text("Payload hasn't initialized yet!", color = Color.Red)
                return@Column
            }
            payload?.let { p ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(Dp.Unspecified, 840.dp)
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Partition Available ${payload?.manifest?.partitionsCount?.let { "($it)" }}", style = AppTheme.typography.h4)
                            Spacer(Modifier.weight(1f))
                            Button(text = "Save All")
                        }
                    }
                    items(p.manifest.partitionsList) { partition ->
                        Spacer(Modifier.height(24.dp))
                        ListItem(partition)
                    }
                }
            }
        }
    }
}

@Composable
fun ListItem(partition: UpdateMetadata.PartitionUpdate?) {
    var sheetState by remember { mutableStateOf(false) }
    if (partition == null) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {
            sheetState = !sheetState
            Log.d("HASH", partition.newPartitionInfo.hash.toString())
        }.padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(52.dp)) {
            if (sheetState) CircularWavyProgressIndicator(
                amplitude = 0f,
                stroke = Stroke(width = 6.5f),
                trackStroke = Stroke(width = 6.5f),
                modifier = Modifier.fillMaxSize()
            )
            Icon(
                Icons.Default.Album,
                contentDescription = null,
                Modifier.animateContentSize().size(36.dp).align(Alignment.Center)
                    .padding(if (sheetState) 4.dp else 0.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text("${partition.partitionName}", style = AppTheme.typography.label1)
            Spacer(Modifier.height(6.dp))

            Text(text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight(600))) {
                    append("${Utils.parseSize(partition.newPartitionInfo.size)} ")
                }
                withStyle(SpanStyle(background = AppTheme.colors.primaryContainer.copy(alpha = .08f))) {
                    append(" pending...")
                }
            }, style = AppTheme.typography.body3)
        }
        Spacer(Modifier.weight(1f))
        Button(variant = ButtonVariant.PrimaryOutlined) {
            Text("Save", style = AppTheme.typography.button)
        }
    }

    ModalBottomSheet(
        modifier = Modifier.padding(top = 100.dp),
        isVisible = sheetState,
        onDismissRequest = { sheetState = false }) {

        val clipManager = LocalClipboard.current
        Column(
            Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            Text(partition.partitionName, style = AppTheme.typography.h2)
            Spacer(Modifier.height(20.dp))
            Text("Size: ${Utils.parseSize(partition.newPartitionInfo.size)}")

            val hash = partition.newPartitionInfo.hash.decodeToString()
            Row {
                Text("Hash: ")
                Row(
                    modifier = Modifier.background(
                        Color(0xFF101010),
                        RoundedCornerShape(6.dp)
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        hash,
                        color = Color.White,
                        modifier = Modifier
                            .padding(8.dp).weight(1f),
                        fontFamily = FontFamily.Monospace
                    )
                    var copied by remember { mutableStateOf(false) }
                    Image(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clickable(!copied) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    clipManager.setClipEntry(
                                        ClipEntry(
                                            ClipData.newPlainText(
                                                "hash",
                                                AnnotatedString(hash)
                                            )
                                        )
                                    )
                                    copied = true
                                    delay(3000)
                                    copied = false
                                }
                            },
                        colorFilter = ColorFilter.lighting(Color.Black, Color.White)
                    )
                }
            }
        }
    }
}

private fun ByteString.decodeToString(): String {
    return toByteArray().joinToString("") { "%02x".format(it) }
}
