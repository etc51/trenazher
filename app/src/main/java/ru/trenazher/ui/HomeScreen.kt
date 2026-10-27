package ru.trenazher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.trenazher.data.Repo
import ru.trenazher.data.SectionInfo

/** Главный экран: общий прогресс, «Продолжить», поиск и список разделов по группам. */
@Composable
fun HomeScreen(repo: Repo, onOpen: (String) -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    val groups = repo.index.groupBy { it.group }
    val total = repo.index.sumOf { it.count }
    val learned = repo.index.sumOf { repo.learned(it.code) }
    val last = repo.lastSection?.let { repo.info(it) }
    val nav = WindowInsets.navigationBars.asPaddingValues()

    Centered(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 330.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = nav.calculateBottomPadding() + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Header(learned, total, onSearch, onSettings)
            }
            if (last != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueCard(last, repo.learned(last.code)) { onOpen(last.code) }
                }
            }
            for ((g, list) in groups) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        groupTitle(g),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                    )
                }
                items(list, key = { it.code }) { info ->
                    SectionCard(info, repo.learned(info.code), repo.mistakes(info.code)) { onOpen(info.code) }
                }
            }
        }
    }
}

@Composable
private fun Header(learned: Int, total: Int, onSearch: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(BrandGradient)
                .padding(start = 22.dp, end = 10.dp, top = 14.dp, bottom = 22.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Аттестация\nРостехнадзора",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f).padding(top = 8.dp),
                    )
                    IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.Top)) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Настройки", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Официальные вопросы, ответы со ссылкой на норму",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(end = 12.dp),
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Изучено $learned из $total",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(8.dp))
                        Bar(if (total == 0) 0f else learned / total.toFloat(), color = Amber, height = 8.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Поле поиска открывает отдельный экран: так клавиатура не прыгает на главном.
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(role = Role.Button, onClick = onSearch)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("Найти вопрос", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContinueCard(info: SectionInfo, learned: Int, onClick: () -> Unit) {
    Card(onClick = onClick, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Продолжить", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                Text(
                    "${info.code} ${info.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Bar(learned / info.count.coerceAtLeast(1).toFloat())
            }
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(BrandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun SectionCard(info: SectionInfo, learned: Int, mistakes: Int, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().heightIn(min = 132.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .widthIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(info.code, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                info.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralQuestions(info.count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (mistakes > 0) {
                Text(
                    "ошибок: $mistakes",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAnswerColors.current.wrong,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                if (learned == 0) "не начат" else "${learned * 100 / info.count.coerceAtLeast(1)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Bar(learned / info.count.coerceAtLeast(1).toFloat())
    }
}
