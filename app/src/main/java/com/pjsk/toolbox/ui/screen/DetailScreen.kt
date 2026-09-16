package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.db.MasterRowEntity
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.ui.Presentations
import com.pjsk.toolbox.ui.common.EmptyState
import com.pjsk.toolbox.ui.common.KeyValueRow
import com.pjsk.toolbox.ui.common.RemoteImage
import com.pjsk.toolbox.ui.common.SectionTitle
import com.pjsk.toolbox.ui.common.displayName
import com.pjsk.toolbox.ui.common.jsonObject
import com.pjsk.toolbox.ui.common.prettyPrintJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * 通用详情页：展示该行的关键字段 + 完整原始 JSON。
 *
 * 之所以要展示原始 JSON：这是一个「数据预览工具」，官方随时可能增删字段，
 * 直接把原始数据摊开给用户看，比硬编码一堆可能过期的字段更可靠。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    tableName: String,
    rowId: Int,
    region: ServerRegion,
    onBack: () -> Unit,
) {
    val presentation = remember(tableName) { Presentations[tableName] }
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val dao = remember { container.database.masterDao() }

    var row by remember(tableName, rowId) { mutableStateOf<MasterRowEntity?>(null) }
    var loaded by remember(tableName, rowId) { mutableStateOf(false) }

    LaunchedEffect(tableName, rowId) {
        row = withContext(Dispatchers.IO) { dao.byId(tableName, rowId) }
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(presentation.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val current = row
        when {
            !loaded -> CenteredBox { CircularProgressIndicator() }

            current == null -> EmptyState(
                title = "找不到这条数据",
                description = "它可能在该表最近一次同步时被移除了。",
            )

            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemoteImage(
                        url = presentation.imageUrl(region, current),
                        contentDescription = null,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = current.displayName(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "#${current.id}　·　$tableName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!current.hasChineseNameOrNull()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "简中数据尚未收录，当前显示日文原名",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }

                SectionTitle("字段")
                val obj = current.jsonObject()
                if (obj == null) {
                    Text(
                        text = "该行不是 JSON 对象，请看下方的原始内容。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    var shown = 0
                    obj.forEach { (key, value) ->
                        if (value is JsonPrimitive && value !is JsonNull) {
                            KeyValueRow(key, value.content)
                            shown++
                        }
                    }
                    if (shown == 0) {
                        Text(
                            text = "该行顶层没有简单字段（全部是嵌套结构），请查看原始 JSON。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                SectionTitle("原始 JSON（可长按复制）")
                SelectionContainer {
                    Text(
                        text = current.data.prettyPrintJson(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 该行是否完全没有中文名（用于给出「显示日文原名」的说明）。 */
private fun MasterRowEntity.hasChineseNameOrNull(): Boolean = !nameZh.isNullOrBlank()
