// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.sakugapp.data.*
import com.sakugapp.ui.FeedViewModel
import com.sakugapp.ui.VideoPanel
import com.sakugapp.ui.AboutDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB9C3FF), background = Color(0xFF101114), surface = Color(0xFF191B22),
            )) { Sakugapp() }
        }
    }
}

@Composable
private fun Sakugapp(model: FeedViewModel = viewModel()) {
    var about by rememberSaveable { mutableStateOf(false) }
    if (about) AboutDialog { about = false }
    val state by model.state.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    // Keep this outside the detail branch so returning never recreates the grid state.
    val grid = rememberLazyGridState()
    LaunchedEffect(state.query) { grid.scrollToItem(0) }
    BackHandler(selected != null) { model.close() }
    Scaffold { insets ->
        if (selected != null) {
            PostDetail(selected!!, Modifier.padding(insets), model::close, model::search)
        } else {
            var query by rememberSaveable(state.query) { mutableStateOf(state.query) }
            Column(Modifier.padding(insets).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sakugapp", style = MaterialTheme.typography.headlineLarge)
                    TextButton(onClick = { about = true }) { Text("关于与许可") }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, label = { Text("标签搜索（空格分隔）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { model.search(query) }),
                    trailingIcon = { TextButton(onClick = { model.search(query) }) { Text("搜索") } },
                )
                Text("公开数据来自 Sakugabooru · 未知分类使用中性色", style = MaterialTheme.typography.labelSmall)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2), state = grid, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.categoryWarning?.let { warning ->
                        item(span = { GridItemSpan(2) }) { Text(warning, style = MaterialTheme.typography.bodySmall) }
                    }
                    items(state.posts, key = { it.id }) { post ->
                        PostCard(post, { model.open(post) }, model::search)
                    }
                    item(span = { GridItemSpan(2) }) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在加载真实帖子…") }
                            state.error?.let { message ->
                                Text(message, color = MaterialTheme.colorScheme.error)
                                Button(onClick = model::loadMore) { Text("重试本页") }
                            }
                            if (!state.loading && state.error == null) {
                                if (state.posts.isEmpty()) Text("没有找到帖子。请更换标签搜索。")
                                else if (state.endReached) Text("已加载全部结果")
                                else Button(onClick = model::loadMore, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostCard(post: Post, open: () -> Unit, search: (String) -> Unit) {
    Card {
        TextButton(onClick = open, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
            PostImage(post, Modifier.fillMaxWidth())
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = open, contentPadding = PaddingValues(0.dp)) {
                Text("#${post.id} · Score ${post.score?.toString() ?: "未知"}")
            }
            // All tags remain searchable; only verified types receive semantic colors.
            val copyright = post.tags.filter { it.category == TagCategory.COPYRIGHT }
            val artists = post.tags.filter { it.category == TagCategory.ARTIST }
            val others = post.tags.filter { it.category != TagCategory.COPYRIGHT && it.category != TagCategory.ARTIST }
            val shownTags = (copyright.take(2) + artists.take(2) + others).take(4)
            Tags(shownTags, search)
            if (post.tags.size > shownTags.size) Text("其余 ${post.tags.size - shownTags.size} 个标签见详情", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Tags(tags: List<Tag>, search: (String) -> Unit) {
    Column {
        tags.forEach { tag ->
            val color = when (tag.category) {
                TagCategory.COPYRIGHT -> Color(0xFFFFD54F)
                TagCategory.ARTIST -> Color(0xFFCE93D8)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            TextButton(onClick = { search(tag.name) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)) {
                Text(tag.name, color = color, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PostImage(post: Post, modifier: Modifier = Modifier, detail: Boolean = false) {
    val mediaUrl = if (detail) post.fileUrl ?: post.previewUrl else post.previewUrl
    val ratio = if (detail && post.width != null && post.height != null) post.width.toFloat() / post.height else post.aspectRatio
    val sized = if (ratio != null) modifier.aspectRatio(ratio) else modifier.height(if (detail) 240.dp else 160.dp)
    if (mediaUrl == null) {
        Surface(sized) { Text("该帖子未提供缩略图", Modifier.padding(16.dp)) }
    } else {
        SubcomposeAsyncImage(
            model = mediaUrl, contentDescription = "帖子 #${post.id} 图像", modifier = sized,
            contentScale = ContentScale.Fit,
            loading = { Box(Modifier.padding(16.dp)) { CircularProgressIndicator() } },
            error = { Text("图像加载失败", Modifier.padding(12.dp)) },
        )
    }
}

@Composable
private fun PostDetail(post: Post, modifier: Modifier, back: () -> Unit, search: (String) -> Unit) {
    val uri = LocalUriHandler.current
    var linkError by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = back) { Text("返回列表") }
        Text("帖子 #${post.id}", style = MaterialTheme.typography.headlineMedium)
        if (post.isVideo) VideoPanel(post.fileUrl!!)
        else { PostImage(post, Modifier.fillMaxWidth(), detail = true); Text("该帖子没有支持的视频地址。") }
        Text("Score：${post.score?.toString() ?: "未知"}")
        if (post.width != null && post.height != null) Text("原始尺寸：${post.width} × ${post.height}")
        post.fileExt?.let { Text("文件格式：$it") }
        post.uploader?.let { Text("上传者：$it") }
        post.rating?.let { Text("站点 Rating：$it") }
        post.status?.let { Text("站点状态：$it") }
        post.createdAt?.let { timestamp ->
            val time = runCatching { DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault())) }.getOrNull()
            time?.let { Text("发布于：$it") }
        }
        post.source?.let { Text("Source（原文）：$it") }
        OutlinedButton(onClick = { linkError = runCatching { uri.openUri(post.pageUrl) }.isFailure }) { Text("在浏览器打开原帖") }
        if (linkError) Text("未找到可打开链接的浏览器", color = MaterialTheme.colorScheme.error)
        Text("标签", style = MaterialTheme.typography.titleMedium)
        Tags(post.tags, search)
    }
}
