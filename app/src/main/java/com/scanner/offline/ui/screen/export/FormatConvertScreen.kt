package com.scanner.offline.ui.screen.export

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.scanner.offline.R
import com.scanner.offline.domain.model.ExportFormat
import com.scanner.offline.util.ShareUtils
import com.scanner.offline.util.Time
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatConvertScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var sourceUri by remember { mutableStateOf<String?>(null) }
    var targetFormat by remember { mutableStateOf(ExportFormat.PNG) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        sourceUri = uri?.toString()
    }

    LaunchedEffect(state.error) {
        state.error?.let { scope.launch { snackbar.showSnackbar(it) } }
    }
    LaunchedEffect(state.lastResult) {
        state.lastResult?.let { scope.launch { snackbar.showSnackbar("转换完成：${it.displayName}") } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_format)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (sourceUri != null) {
                        AsyncImage(
                            model = sourceUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { pickImage.launch("image/*") }) {
                                Text("从相册选择图片")
                            }
                        }
                    }
                }
            }

            if (sourceUri != null) {
                Text("目标格式：", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ExportFormat.JPG, ExportFormat.PNG, ExportFormat.WEBP).forEach { f ->
                        FilterChip(
                            selected = f == targetFormat,
                            onClick = { targetFormat = f },
                            label = { Text(f.displayName) }
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.convertImageFromUri(
                            uriString = sourceUri!!,
                            format = targetFormat,
                            baseName = Time.nowDocName("converted")
                        )
                    },
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.running) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (state.running) "转换中..." else "开始转换")
                }

                state.lastResult?.let { result ->
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("已生成：${result.displayName}")
                            Text(result.humanLocation, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            AssistChip(
                                onClick = {
                                    ShareUtils.share(context, result.shareUri, result.mimeType)
                                },
                                label = { Text("分享") },
                                leadingIcon = { Icon(Icons.Outlined.IosShare, null) }
                            )
                        }
                    }
                }
            }
        }
    }
}
