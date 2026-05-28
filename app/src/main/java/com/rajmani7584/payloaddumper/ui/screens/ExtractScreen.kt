package com.rajmani7584.payloaddumper.ui.screens

import android.content.ClipData
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.protobuf.ByteString
import com.rajmani7584.payloaddumper.LocalSettings
import com.rajmani7584.payloaddumper.MainActivity
import com.rajmani7584.payloaddumper.R
import com.rajmani7584.payloaddumper.model.DataModel
import com.rajmani7584.payloaddumper.model.PartStatus
import com.rajmani7584.payloaddumper.model.PartitionState
import com.rajmani7584.payloaddumper.model.PayloadState
import com.rajmani7584.payloaddumper.model.SettingParam
import com.rajmani7584.payloaddumper.model.Utils
import com.rajmani7584.payloaddumper.ui.components.AppTheme
import com.rajmani7584.payloaddumper.ui.components.LocalColors
import com.rajmani7584.payloaddumper.ui.components.LocalContentColor
import com.rajmani7584.payloaddumper.ui.components.components.Badge
import com.rajmani7584.payloaddumper.ui.components.components.Button
import com.rajmani7584.payloaddumper.ui.components.components.ButtonVariant
import com.rajmani7584.payloaddumper.ui.components.components.Chip
import com.rajmani7584.payloaddumper.ui.components.components.IconButton
import com.rajmani7584.payloaddumper.ui.components.components.IconButtonVariant
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
fun ExtractScreen(payloadState: PayloadState, appNavController: NavHostController, homeNavController: NavHostController) {

    val dataViewModel: DataModel = viewModel(LocalActivity.current as MainActivity)
    val settings by LocalSettings.current.settings.collectAsStateWithLifecycle()
    val cs = rememberCoroutineScope()
    val outputDirectory by dataViewModel.outputDirectory.collectAsState()
    val externalStorage = dataViewModel.externalStorage

    val listState = rememberLazyListState()
    val scrollBehavior = TopBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(
        settings.concurrency,
        settings.autoDelete,
        settings.verifyHash,
        settings.bufferSize
    ) {
        dataViewModel.setSettingParam(
            SettingParam(
                settings.concurrency, settings.verifyHash, settings.autoDelete, settings.overwrite, settings.bufferSize
            )
        )
    }

    Scaffold(topBar = {
        ScreenTopBar(
            title = (payloadState as PayloadState.Ready).name,
            nav = true,
            onNavClick = { homeNavController.popBackStack(Screens.Home.route, false) },
            scrollBehavior = scrollBehavior
        )
    }) { innerPadding ->
        Column(
            Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (payloadState !is PayloadState.Ready) {
                Text(stringResource(R.string.extract_payload_not_init))
                return@Column
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(Dp.Unspecified, 840.dp)
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    val canExtract = payloadState.partitions.none { it.incremental }

                    if (canExtract) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.extract_save_to), style = AppTheme.typography.label1)
                            Chip(
                                Modifier
                                    .weight(1f)
                                    .padding(8.dp)
                                    .horizontalScroll(rememberScrollState(Int.MAX_VALUE))
                                    .background(
                                        Color.White.copy(.25f, .85f, .90f, .95f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 2.dp)
                            ) {
                                outputDirectory.replace(externalStorage, stringResource(R.string.txt_internal_storage))
                                    .split("/")
                                    .forEachIndexed { index, p ->
                                        if (index != 0)
                                            Icon(
                                                Icons.AutoMirrored.Default.KeyboardArrowRight,
                                                contentDescription = null
                                            )
                                        Text(p)
                                    }
                            }
                            IconButton(variant = IconButtonVariant.Ghost, onClick = {
                                appNavController.navigate(Screens.Selector.createRoute(true)) {
                                    popUpTo(Screens.Home.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                }
                            }) {
                                Icon(
                                    Icons.Default.Edit,
                                    modifier = Modifier.padding(4.dp),
                                    contentDescription = "Change out dir"
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.extract_parts_avail, payloadState.partitions.size),
                            style = AppTheme.typography.h4
                        )
                        Spacer(Modifier.weight(1f))
                        dataViewModel.hasPermission.value?.let { perm ->
                            if (!perm) {
                                val activity = LocalActivity.current
                                Button(
                                    text = stringResource(R.string.first_start_allow_file_access),
                                    onClick = {
                                        if (activity == null) return@Button
                                        dataViewModel.requestPermission(activity)
                                    })
                            } else {
                                if (payloadState.partitions.any { it.status == PartStatus.PENDING || it.status == PartStatus.RUNNING }) {
                                    Button(
                                        variant = ButtonVariant.PrimaryOutlined,
                                        text = stringResource(R.string.extract_ops_cancel_all),
                                        onClick = {
                                            cs.launch {
                                                dataViewModel.cancelAll()
                                            }
                                        })
                                } else {
                                    Button(
                                        enabled = canExtract,
                                        text = stringResource(R.string.extract_ops_save_all),
                                        onClick = {
                                            cs.launch {
                                                dataViewModel.dumpAll()
                                            }
                                        })
                                }
                            }
                        }
                    }
                }
                itemsIndexed(items = payloadState.partitions, key = { _, p -> p.id }) { _, p ->
                    Spacer(Modifier.height(12.dp))
                    ListItem(
                        enabled = dataViewModel.hasPermission.value == true,
                        partition = p,
                        onDump = {
                            cs.launch {
                                dataViewModel.dump(p)
                            }
                        },
                        onCancel = {
                            cs.launch {
                                dataViewModel.cancel(p)
                            }
                        })
                }
            }
        }
    }
}

@Composable
fun ListItem(enabled: Boolean = true, partition: PartitionState, onDump: () -> Unit, onCancel: () -> Unit) {
    var sheetState by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = partition.progress,
        animationSpec = tween(
            durationMillis = 300,
            easing = LinearEasing
        ),
        label = "progress_${partition.id}"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable {
                sheetState = !sheetState
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(Modifier.size(48.dp)) {
            if (partition.status == PartStatus.RUNNING)
                CircularWavyProgressIndicator(
                    progress = { animatedProgress },
                    amplitude = { if (animatedProgress !in .05f.. .90f) 0f else 6f },
                    stroke = Stroke(width = 4f, cap = StrokeCap.Round),
                    trackStroke = Stroke(width = 4f),
                    modifier = Modifier.fillMaxSize()
                )
            else if (partition.status == PartStatus.PENDING || partition.status == PartStatus.VERIFYING)
                CircularWavyProgressIndicator(
                    amplitude = 0f,
                    stroke = Stroke(width = 4f, cap = StrokeCap.Round),
                    trackStroke = Stroke(width = 4f),
                    modifier = Modifier.fillMaxSize()
                )
            Icon(
                Icons.Default.Album,
                contentDescription = null,
                Modifier
                    .animateContentSize()
                    .size(36.dp)
                    .align(Alignment.Center)
                    .padding(if (partition.status == PartStatus.PENDING || partition.status == PartStatus.RUNNING || partition.status == PartStatus.VERIFYING) 4.dp else 0.dp),
                tint = LocalColors.current.onSurface
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(partition.name, style = AppTheme.typography.body1)
            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Utils.parseSize(partition.size),
                    style = AppTheme.typography.label2
                )
                val txtColor = when (partition.status) {
                    PartStatus.RUNNING -> stringResource(R.string.partition_status_progress, (partition.progress * 100).toInt()) to LocalColors.current.primary
                    PartStatus.PENDING -> stringResource(R.string.partition_status_pending) to LocalColors.current.primary
                    PartStatus.VERIFYING -> stringResource(R.string.partition_status_verifying) to LocalColors.current.primary
                    PartStatus.COMPLETED -> stringResource(R.string.partition_status_done) to LocalColors.current.primary
                    PartStatus.FAILED -> stringResource(R.string.partition_status_failed) to LocalColors.current.error
                    else -> "" to Color.White
                }
                if (partition.status != PartStatus.IDLE) {
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .background(LocalColors.current.primary, CircleShape)
                            .size(4.dp)
                    )
                    Badge(containerColor = txtColor.second) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(txtColor.first)
                            if (partition.status == PartStatus.FAILED) {
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = LocalContentColor.current
                                )
                            }
                        }
                    }
                }

                if (partition.incremental) {
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .background(LocalColors.current.primary, CircleShape)
                            .size(4.dp)
                    )
                    Badge(containerColor = LocalColors.current.error) {
                        Text(text = "Incremental")
                    }
                }
            }

//            Badge {
//                Text(text = buildAnnotatedString {
//                    withStyle(SpanStyle(fontWeight = FontWeight(600))) {
//                        append("${Utils.parseSize(partition.size)} ")
//                    }
//                    withStyle(SpanStyle(background = AppTheme.colors.primaryContainer.copy(alpha = .08f))) {
//                        when (partition.status) {
//                            PartStatus.PENDING -> append(" pending...")
//                            PartStatus.RUNNING -> append(" extracting...")
//                            PartStatus.COMPLETED -> append(" saved")
//                            PartStatus.FAILED -> append(" failed! tap for info")
//                            else -> {}
//                        }
//                    }
//            }, style = AppTheme.typography.body3)
//        }
        }
        Spacer(Modifier.weight(1f))
        when (partition.status) {
            PartStatus.PENDING, PartStatus.RUNNING -> {
                IconButton(onClick = onCancel, variant = IconButtonVariant.Ghost) {
                    Icon(Icons.Default.Close, contentDescription = "cancel")
                }
            }

            else -> {
                Button(
                    enabled = !partition.incremental && enabled,
                    variant = ButtonVariant.PrimaryOutlined,
                    onClick = onDump
                ) {
                    Text(stringResource(R.string.extract_ops_save), style = AppTheme.typography.button)
                }
            }
        }
    }

    ModalBottomSheet(
        modifier = Modifier.padding(top = 100.dp),
        isVisible = sheetState,
        onDismissRequest = { sheetState = false }) {

        val clipManager = LocalClipboard.current
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.partition_details_header), style = AppTheme.typography.h1)
            Text(stringResource(R.string.partition_details_name, partition.name))
            Spacer(Modifier.height(24.dp))
            partition.downloadSize?.let {
                Text(stringResource(R.string.partition_details_download_size, Utils.parseSize(it)))
                Spacer(Modifier.height(12.dp))
            }
            Text(stringResource(R.string.partition_details_size, Utils.parseSize(partition.size)))
            Spacer(Modifier.height(12.dp))

            val hash = partition.hash?.decodeToString() ?: "Couldn't get hash"
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.partition_details_hash))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFF101010),
                            RoundedCornerShape(6.dp)
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Box {
                            Text(
                                hash,
                                color = Color.White,
                                modifier = Modifier
                                    .padding(8.dp),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(Modifier.width(2.dp))
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
            partition.error?.let {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.partition_details_error))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Color.Black).padding(12.dp)
                ) {
                    Text(it, color = Color.Red.copy(green = .4f))
                }
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}

fun ByteString.decodeToString(): String {
    return toByteArray().joinToString("") { "%02x".format(it) }
}
