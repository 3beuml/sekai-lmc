package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.remote.DataModule
import com.pjsk.toolbox.data.remote.TableCatalog
import com.pjsk.toolbox.data.remote.TableSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「全部数据表」浏览页。
 *
 * 存在的理由：数据同步页只管下载，而 68 张表里只有少数几张在首页有入口。
 * 这个页面把整个数据目录按模块摊开，显示每张表的体积与已下载行数，
 * 点进去就是通用的列表页 —— 这样任何一张表都可以被浏览到，
 * 而不需要为它单独写一个页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableIndexScreen(
    onBack: () -> Unit,
    onOpenTable: (String) -> Unit,
) {
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val dao = remember { container.database.masterDao() }

    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        counts = withContext(Dispatchers.IO) {
            TableCatalog.ALL.associate { spec -> spec.fileName to dao.count(spec.fileName) }
        }
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全部数据表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) {
            CenteredBox(modifier = Modifier.padding(padding).fillMaxSize()) { CircularProgressIndicator() }
            return@Scaffold
        }

        val downloaded = counts.count { it.value > 0 }
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item(key = "summary") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "共 ${TableCatalog.ALL.size} 张表，已下载 $downloaded 张",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "体积是整个模块的实测大小。点任意一张表即可浏览其内容；" +
                            "没有内容的表请先到「数据同步」勾选对应模块并同步。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }

            DataModule.entries.forEach { module ->
                val specs = TableCatalog.forModules(setOf(module))
                if (specs.isEmpty()) return@forEach

                item(key = "header_${module.id}") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            text = module.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "${specs.size} 张表 · 合计 ${TableCatalog.formatBytes(TableCatalog.totalBytes(setOf(module)))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(specs, key = { it.fileName }) { spec ->
                    TableRow(
                        spec = spec,
                        rowCount = counts[spec.fileName] ?: 0,
                        onClick = { onOpenTable(spec.fileName) },
                    )
                    HorizontalDivider()
                }
            }

            item(key = "footer") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TableRow(
    spec: TableSpec,
    rowCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = spec.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (rowCount > 0) {
                    "已下载 $rowCount 行"
                } else {
                    "未下载"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (rowCount > 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = TableCatalog.formatBytes(spec.approxBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
