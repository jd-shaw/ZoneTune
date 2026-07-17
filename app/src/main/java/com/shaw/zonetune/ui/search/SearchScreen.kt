package com.shaw.zonetune.ui.search

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shaw.zonetune.ui.components.StudioEmptyState
import com.shaw.zonetune.ui.components.StudioSearchField
import com.shaw.zonetune.ui.components.TrackListRow

@Composable
fun SearchScreen(
    pendingQuery: String? = null,
    onPendingQueryConsumed: () -> Unit = {},
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory()),
) {
    val ui by viewModel.ui.collectAsState()
    val history by viewModel.history.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun hideKeyboard() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    val dismissKeyboardModifier = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { hideKeyboard() },
    )

    val resultsListState = rememberLazyListState()
    LaunchedEffect(resultsListState.isScrollInProgress) {
        if (resultsListState.isScrollInProgress) {
            hideKeyboard()
        }
    }

    LaunchedEffect(pendingQuery) {
        val q = pendingQuery?.trim().orEmpty()
        if (q.isNotEmpty()) {
            hideKeyboard()
            viewModel.onQueryChange(q)
            viewModel.search()
            onPendingQueryConsumed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(dismissKeyboardModifier)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (ui.loggedIn) {
                        "已登录 · ${ui.userName}"
                    } else {
                        "从关键词开始，进入工作室"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        StudioSearchField(
            value = ui.query,
            onValueChange = viewModel::onQueryChange,
            onSearch = {
                hideKeyboard()
                viewModel.search()
            },
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(
            modifier = Modifier
                .height(8.dp)
                .fillMaxWidth()
                .then(dismissKeyboardModifier),
        )

        val showResultList = !ui.loading && ui.error == null && ui.results.isNotEmpty()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (showResultList) Modifier else dismissKeyboardModifier),
        ) {
            when {
                ui.loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "搜索中…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ui.error != null -> {
                    Text(
                        text = ui.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(20.dp),
                    )
                }

                ui.results.isEmpty() -> {
                    if (!ui.hasSearched) {
                        SearchHistorySection(
                            history = history,
                            onSelect = {
                                hideKeyboard()
                                viewModel.searchFromHistory(it)
                            },
                            onRemove = viewModel::removeHistory,
                            onClear = viewModel::clearHistory,
                        )
                    } else {
                        StudioEmptyState(
                            title = "没有找到结果",
                            subtitle = "换个关键词再试，例如：洛天依 / 钢琴 / 现场",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = resultsListState,
                        contentPadding = PaddingValues(bottom = 12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(ui.results, key = { _, track -> track.id }) { index, track ->
                            TrackListRow(
                                track = track,
                                onClick = {
                                    hideKeyboard()
                                    viewModel.play(track)
                                },
                                showDivider = index != ui.results.lastIndex,
                                enterDelayMs = (index * 28).coerceAtMost(220),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistorySection(
    history: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) {
        StudioEmptyState(
            title = "从关键词开始",
            subtitle = "例如：洛天依 / 钢琴 / 现场\n搜过的词会出现在这里",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = "搜索记录",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) {
                Text(
                    text = "清空",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            history.forEach { keyword ->
                HistoryChip(
                    text = keyword,
                    onClick = { onSelect(keyword) },
                    onRemove = { onRemove(keyword) },
                )
            }
        }
    }
}

@Composable
private fun HistoryChip(
    text: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
