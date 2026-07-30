package com.tabdeck.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.GlanceTheme
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.tabdeck.app.MainActivity
import com.tabdeck.app.TabDeckViewModel
import com.tabdeck.app.data.TabDeckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TabDeckWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = withContext(Dispatchers.IO) { TabDeckRepository(context).currentState() }
        val health = collectionHealth(
            active = state.stats.active,
            duplicates = state.stats.duplicateCopies,
            inbox = state.stats.inbox,
            stale = state.stats.stale,
        )
        provideContent {
            GlanceTheme {
                WidgetContent(
                    context = context,
                    active = state.stats.active,
                    duplicates = state.stats.duplicateCopies,
                    inbox = state.stats.inbox,
                    health = health,
                    bridgeActive = state.bridgeSession.enabled,
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(
    context: Context,
    active: Int,
    duplicates: Int,
    inbox: Int,
    health: Int,
    bridgeActive: Boolean,
) {
    val libraryIntent = intent(context, TabDeckViewModel.ACTION_OPEN_LIBRARY)
    val importIntent = intent(context, TabDeckViewModel.ACTION_OPEN_IMPORT)
    val transferIntent = intent(context, TabDeckViewModel.ACTION_OPEN_TRANSFER)
    val automateIntent = intent(context, TabDeckViewModel.ACTION_OPEN_AUTOMATE)
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.Vertical.Top,
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text("TabDeck", style = TextStyle(fontWeight = FontWeight.Bold))
                Text("Android browser control", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
            }
            Text(if (bridgeActive) "Bridge live" else "Health $health", style = TextStyle(color = GlanceTheme.colors.secondary, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(10.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            Metric("Active", active.toString(), GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            Metric("Dupes", duplicates.toString(), GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            Metric("Inbox", inbox.toString(), GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(10.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            WidgetAction("Import", importIntent, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            WidgetAction("Library", libraryIntent, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            WidgetAction("Rules", automateIntent, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            WidgetAction("Send", transferIntent, GlanceModifier.defaultWeight())
        }
    }
}

private fun intent(context: Context, actionName: String): Intent = Intent(context, MainActivity::class.java).apply { action = actionName }

@Composable
private fun Metric(label: String, value: String, modifier: GlanceModifier) {
    Column(modifier.padding(7.dp), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
        Text(value, style = TextStyle(fontWeight = FontWeight.Bold))
        Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
    }
}

@Composable
private fun WidgetAction(label: String, intent: Intent, modifier: GlanceModifier) {
    Row(
        modifier = modifier
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(12.dp)
            .clickable(actionStartActivity(intent))
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

class TabDeckWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TabDeckWidget()
}

class QuickCaptureWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = withContext(Dispatchers.IO) { TabDeckRepository(context).currentState() }
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier.fillMaxSize()
                        .appWidgetBackground()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(22.dp)
                        .padding(14.dp),
                ) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Column(GlanceModifier.defaultWeight()) {
                            Text("Quick control", style = TextStyle(fontWeight = FontWeight.Bold))
                            Text("${state.stats.active} active · ${state.stats.duplicateCopies} dupes", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                        }
                        Text(
                            if (state.bridgeSession.enabled) "Bridge live" else "Local",
                            style = TextStyle(color = GlanceTheme.colors.secondary, fontWeight = FontWeight.Bold),
                        )
                    }
                    Spacer(GlanceModifier.height(10.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        WidgetAction("Import", intent(context, TabDeckViewModel.ACTION_OPEN_IMPORT), GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetAction("Library", intent(context, TabDeckViewModel.ACTION_OPEN_LIBRARY), GlanceModifier.defaultWeight())
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        WidgetAction("Transfer", intent(context, TabDeckViewModel.ACTION_OPEN_TRANSFER), GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetAction("Connect", intent(context, TabDeckViewModel.ACTION_OPEN_CONNECT), GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
}

class QuickCaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickCaptureWidget()
}
