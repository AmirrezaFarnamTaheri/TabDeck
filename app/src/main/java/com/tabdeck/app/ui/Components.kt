package com.tabdeck.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tabdeck.app.engine.UrlNormalizer
import com.tabdeck.app.model.BrowserId
import com.tabdeck.app.model.TabItem
import com.tabdeck.app.model.ViewDensity

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
            )
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        actions?.let {
            Spacer(Modifier.width(16.dp))
            it()
        }
    }
}

@Composable
fun HeroPanel(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null,
) {
    val gradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
        ),
    )
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
        Column(
            Modifier.background(gradient).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)) {
                Icon(icon, null, Modifier.padding(12.dp).size(26.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content?.invoke()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetricTile(
    label: String,
    value: String,
    helper: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val cardModifier = if (onClick == null) modifier else modifier.combinedClickable(onClick = onClick)
    ElevatedCard(
        modifier = cardModifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action?.invoke()
    }
}

@Composable
fun ControlCard(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    ElevatedCard(modifier, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(icon, null, Modifier.padding(10.dp).size(21.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                trailing?.invoke()
            }
            content?.invoke()
        }
    }
}

@Composable
fun BrowserBadge(browser: BrowserId, installed: Boolean? = null) {
    AssistChip(
        onClick = {},
        label = { Text(browser.displayName) },
        leadingIcon = {
            Icon(
                if (installed == false) Icons.Outlined.Circle else Icons.Outlined.CheckCircle,
                null,
                Modifier.size(17.dp),
                tint = when (installed) {
                    true -> MaterialTheme.colorScheme.secondary
                    false -> MaterialTheme.colorScheme.outline
                    null -> MaterialTheme.colorScheme.primary
                },
            )
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabCard(
    tab: TabItem,
    selected: Boolean,
    density: ViewDensity,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onTogglePinned: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vertical = when (density) {
        ViewDensity.COMFORTABLE -> 14.dp
        ViewDensity.COMPACT -> 9.dp
        ViewDensity.DENSE -> 6.dp
    }
    val shape = when (density) {
        ViewDensity.COMFORTABLE -> 20.dp
        ViewDensity.COMPACT -> 15.dp
        ViewDensity.DENSE -> 12.dp
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(role = Role.Button, onClick = onDetails, onLongClick = onToggle)
            .semantics {
                this.selected = selected
                contentDescription = "${tab.title.ifBlank { UrlNormalizer.host(tab.url) }}, ${tab.browser.displayName}, ${tab.assignedGroup}"
            },
        shape = RoundedCornerShape(shape),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (density == ViewDensity.DENSE) 8.dp else 12.dp, vertical = vertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (density == ViewDensity.DENSE) 7.dp else 10.dp),
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            if (density != ViewDensity.DENSE) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Outlined.Language, null, Modifier.padding(9.dp).size(19.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    tab.title.ifBlank { UrlNormalizer.host(tab.url).ifBlank { "Untitled tab" } },
                    style = if (density == ViewDensity.DENSE) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    tab.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (density == ViewDensity.COMFORTABLE) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MiniPill(tab.assignedGroup)
                        MiniPill(tab.browser.displayName)
                        if (tab.sourceGroup.isNotBlank() && tab.sourceGroup != tab.assignedGroup) MiniPill("From ${tab.sourceGroup}")
                        tab.tags.take(3).forEach { MiniPill("#$it") }
                        if (tab.notes.isNotBlank()) MiniPill("Note")
                        if (tab.transferCount > 0) MiniPill("Sent ${tab.transferCount}×")
                    }
                }
            }
            IconButton(onClick = onTogglePinned) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = if (tab.pinned) "Unpin" else "Pin",
                    tint = if (tab.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            if (density != ViewDensity.DENSE) {
                IconButton(onClick = onOpen) { Icon(Icons.Outlined.Tab, contentDescription = "Open") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabGridCard(
    tab: TabItem,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onTogglePinned: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.combinedClickable(role = Role.Button, onClick = onDetails, onLongClick = onToggle)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Outlined.Language, null, Modifier.padding(9.dp).size(19.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.weight(1f))
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
            }
            Text(
                tab.title.ifBlank { UrlNormalizer.host(tab.url).ifBlank { "Untitled tab" } },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(tab.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniPill(tab.assignedGroup)
                MiniPill(tab.browser.displayName)
                tab.tags.take(2).forEach { MiniPill("#$it") }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onTogglePinned) {
                    Icon(Icons.Outlined.PushPin, if (tab.pinned) "Unpin" else "Pin", tint = if (tab.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = onOpen) { Icon(Icons.Outlined.Tab, "Open") }
                IconButton(onClick = onDetails) { Icon(Icons.Outlined.MoreVert, "Details") }
            }
        }
    }
}

@Composable
fun MiniPill(text: String, color: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Surface(shape = CircleShape, color = color) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 44.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(icon, null, Modifier.padding(18.dp).size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SecurityPill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProgressStrip(progress: Float, label: String, helper: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(helper, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape))
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
