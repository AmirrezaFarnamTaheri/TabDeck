package com.tabdeck.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.tabdeck.app.MainActivity
import com.tabdeck.app.TabDeckViewModel
import com.tabdeck.app.data.TabDeckRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class TabDeckWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        val health = collectionHealth(state.stats.active, state.stats.duplicateCopies, state.stats.inbox, state.stats.stale)
        provideContent {
            GlanceTheme {
                Column(widgetSurface()) {
                    WidgetHeader("TabDeck", if (state.bridgeSession.enabled) "Bridge live" else "Health $health")
                    Spacer(GlanceModifier.height(10.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        Metric("Active", state.stats.active.toString(), GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(6.dp))
                        Metric("Dupes", state.stats.duplicateCopies.toString(), GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(6.dp))
                        Metric("Inbox", state.stats.inbox.toString(), GlanceModifier.defaultWeight())
                    }
                    Spacer(GlanceModifier.height(10.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        WidgetAction("Import", intent(context, TabDeckViewModel.ACTION_OPEN_IMPORT), GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetAction("Tabs", intent(context, TabDeckViewModel.ACTION_OPEN_LIBRARY), GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetAction("Open", intent(context, TabDeckViewModel.ACTION_OPEN_TRANSFER), GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
}

class QuickCaptureWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        provideContent {
            GlanceTheme {
                Column(widgetSurface()) {
                    WidgetHeader("Quick capture", "${state.stats.active} saved")
                    Spacer(GlanceModifier.height(10.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        WidgetAction("Import", intent(context, TabDeckViewModel.ACTION_OPEN_IMPORT), GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetAction("Capture", intent(context, TabDeckViewModel.ACTION_OPEN_CONNECT), GlanceModifier.defaultWeight())
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    WidgetAction("Open saved tabs", intent(context, TabDeckViewModel.ACTION_OPEN_TRANSFER), GlanceModifier.fillMaxWidth())
                }
            }
        }
    }
}

class TransferStatusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        val event = state.transferHistory.firstOrNull()
        val maintenance = state.maintenanceStatus
        provideContent {
            GlanceTheme {
                Column(widgetSurface()) {
                    WidgetHeader("Recent activity", if (event == null) "Ready" else event.targetBrowser.displayName)
                    Spacer(GlanceModifier.height(8.dp))
                    if (event == null) {
                        Text("No open requests recorded yet", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                    } else {
                        Text(
                            "${event.opened}/${event.attempted} dispatched · ${event.failed} failed${if (event.cancelled) " · stopped" else ""}",
                            style = TextStyle(fontWeight = FontWeight.Bold),
                        )
                        Text(formatWidgetTime(event.createdAtEpochMs), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    val maintenanceText = when {
                        maintenance.lastRunAtEpochMs == null -> "Maintenance has not run"
                        maintenance.failed -> "Maintenance needs attention"
                        else -> "Maintenance: ${maintenance.awakened} restored · ${maintenance.pruned} pruned"
                    }
                    Text(maintenanceText, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                    Spacer(GlanceModifier.height(8.dp))
                    WidgetAction("Open activity", intent(context, TabDeckViewModel.ACTION_OPEN_TRANSFER), GlanceModifier.fillMaxWidth())
                }
            }
        }
    }
}

class DeckLauncherWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        val deck = state.decks.firstOrNull()
        provideContent {
            GlanceTheme {
                Column(widgetSurface()) {
                    WidgetHeader("Deck launcher", deck?.let { "${it.tabCount} tabs" } ?: "No deck")
                    Spacer(GlanceModifier.height(8.dp))
                    if (deck == null) {
                        Text("Create a deck in Organize to pin a reusable launch set.", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                        Spacer(GlanceModifier.height(8.dp))
                        WidgetAction("Manage decks", intent(context, TabDeckViewModel.ACTION_OPEN_AUTOMATE), GlanceModifier.fillMaxWidth())
                    } else {
                        Text(deck.name, style = TextStyle(fontWeight = FontWeight.Bold))
                        if (deck.description.isNotBlank()) {
                            Text(deck.description.take(100), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                        }
                        Spacer(GlanceModifier.height(8.dp))
                        WidgetAction(
                            "Choose browser and open",
                            intent(context, TabDeckViewModel.ACTION_OPEN_DECK).putExtra(TabDeckViewModel.EXTRA_DECK_ID, deck.id),
                            GlanceModifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

suspend fun updateAllTabDeckWidgets(context: Context) {
    listOf(
        TabDeckWidget(),
        QuickCaptureWidget(),
        TransferStatusWidget(),
        DeckLauncherWidget(),
    ).forEach { widget ->
        try {
            widget.updateAll(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Widgets are derived UI. A launcher/rendering failure must not fail a completed data mutation.
        }
    }
}

private suspend fun loadState(context: Context) = withContext(Dispatchers.IO) {
    TabDeckRepository(context).currentState()
}

@Composable
private fun widgetSurface(): GlanceModifier = GlanceModifier.fillMaxSize()
    .appWidgetBackground()
    .background(GlanceTheme.colors.widgetBackground)
    .cornerRadius(22.dp)
    .padding(14.dp)

private fun intent(context: Context, actionName: String): Intent = Intent(context, MainActivity::class.java).apply {
    action = actionName
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}

@Composable
private fun WidgetHeader(title: String, status: String) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(title, style = TextStyle(fontWeight = FontWeight.Bold))
            Text("TabDeck", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
        }
        Text(status, style = TextStyle(color = GlanceTheme.colors.secondary, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: GlanceModifier) {
    Column(modifier.padding(7.dp), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
        Text(value, style = TextStyle(fontWeight = FontWeight.Bold))
        Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
    }
}

@Composable
private fun WidgetAction(label: String, actionIntent: Intent, modifier: GlanceModifier) {
    Row(
        modifier = modifier
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(12.dp)
            .clickable(actionStartActivity(actionIntent))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(label, style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onPrimaryContainer))
    }
}

private fun collectionHealth(active: Int, duplicates: Int, inbox: Int, stale: Int): Int {
    if (active <= 0) return 100
    val penalty = ((duplicates * 35.0 + inbox * 20.0 + stale * 25.0) / active).toInt()
    return (100 - penalty).coerceIn(0, 100)
}

internal fun formatWidgetTime(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))

class TabDeckWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TabDeckWidget() }
class QuickCaptureWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickCaptureWidget() }
class TransferStatusWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TransferStatusWidget() }
class DeckLauncherWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DeckLauncherWidget() }
