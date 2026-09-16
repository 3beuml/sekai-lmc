package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.db.MasterDao
import com.pjsk.toolbox.data.db.MasterRowEntity
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.ui.Presentations
import com.pjsk.toolbox.ui.common.EmptyState
import com.pjsk.toolbox.ui.common.MasterRowItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_SIZE = 50

/**
 * 通用列表页：**一张实现覆盖全部模块**。
 *
 * 展示方式（标题、缩略图、副标题）由 [Presentations] 按表名决定，
 * 所以音乐/卡牌/角色/服装/活动/扭蛋/贴纸都走这里，没有重复代码。
 *
 * 三种状态都必须处理：未下载（引导去同步）、加载中、有数据。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterListScreen(
    tableName: String,
    region: ServerRegion,
    onBack: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    val presentation = remember(tableName) { Presentations[tableName] }
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val dao = remember { container.database.masterDao() }
    val scope = rememberCoroutineScope()

    var total by remember(tableName) { mutableStateOf(-1) }
    var rows by remember(tableName) { mutableStateOf<List<MasterRowEntity>>(emptyList()) }
    var query by rememberSaveable(tableName) { mutableStateOf("") }
    var loading by remember(tableName) { mutableStateOf(true) }
    var loadingMore by remember(tableName) { mutableStateOf(false) }

    LaunchedEffect(tableName, query) {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            queryPage(dao, tableName, query, PAGE_SIZE, 0)
        }
        val count = withContext(Dispatchers.IO) { dao.count(tableName) }
        rows = loaded
        total = count
        loading = false
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索（中文名或日文名）") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                loading && rows.isEmpty() -> CenteredBox(modifier = Modifier.weight(1f)) {
                    CircularProgressIndicator()
                }

                total == 0 && query.isBlank() -> Box(modifier = Modifier.weight(1f)) {
                    EmptyState(
                        title = "这个模块还没有下载",
                        description = presentation.emptyHint,
                        actionLabel = "去数据同步",
                        onAction = onOpenSync,
                    )
                }

                rows.isEmpty() -> Box(modifier = Modifier.weight(1f)) {
                    EmptyState(
                        title = "没有匹配的结果",
                        description = "换个关键词试试；也可以清空搜索框查看全部内容。",
                    )
                }

                else -> {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(rows, key = { it.id }) { row ->
                            MasterRowItem(
                                row = row,
                                subtitle = presentation.subtitle(row),
                                imageUrl = presentation.imageUrl(region, row),
                                onClick = { onOpenDetail(row.id) },
                            )
                            HorizontalDivider()
                        }

                        if (rows.size >= PAGE_SIZE) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    OutlinedButton(
                                        enabled = !loadingMore,
                                        onClick = {
                                            scope.launch {
                                                loadingMore = true
                                                val next = withContext(Dispatchers.IO) {
                                                    queryPage(dao, tableName, query, PAGE_SIZE, rows.size)
                                                }
                                                rows = rows + next
                                                loadingMore = false
                                            }
                                        },
                                    ) {
                                        Text(if (loadingMore) "加载中…" else "加载更多")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun queryPage(
    dao: MasterDao,
    tableName: String,
    query: String,
    limit: Int,
    offset: Int,
): List<MasterRowEntity> =
    if (query.isBlank()) {
        dao.page(tableName, limit, offset)
    } else {
        dao.search(tableName, query.trim(), limit, offset)
    }

/** 居中容器。默认占满，但在 Column 里调用时应传入 `Modifier.weight(1f)`。 */
@Composable
internal fun CenteredBox(
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}
