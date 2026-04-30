package com.scanner.offline.ui.screen.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scanner.offline.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onScanClick: () -> Unit,
    onPickImageForOcr: (String) -> Unit,
    onFormatConvertClick: () -> Unit
) {
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.toString()?.let(onPickImageForOcr)
    }

    val tools = listOf(
        Tool(Icons.Outlined.CameraAlt, R.string.tool_scan, R.string.tool_scan_desc, onScanClick),
        Tool(Icons.Outlined.TextFields, R.string.tool_ocr, R.string.tool_ocr_desc) { pickImage.launch("image/*") },
        Tool(Icons.Outlined.PictureAsPdf, R.string.tool_to_pdf, R.string.tool_to_pdf_desc) { pickImage.launch("image/*") },
        Tool(Icons.Outlined.Description, R.string.tool_to_word, R.string.tool_to_word_desc) { pickImage.launch("image/*") },
        Tool(Icons.Outlined.GridOn, R.string.tool_to_excel, R.string.tool_to_excel_desc) { pickImage.launch("image/*") },
        Tool(Icons.Outlined.Image, R.string.tool_format, R.string.tool_format_desc, onFormatConvertClick)
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tools_title)) }) }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = tools, key = { it.titleRes }) { tool ->
                ToolCard(tool = tool)
            }
        }
    }
}

private data class Tool(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolCard(tool: Tool) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = tool.onClick
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(tool.titleRes),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(tool.descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
