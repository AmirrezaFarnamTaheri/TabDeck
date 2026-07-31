package com.tabdeck.app.transfer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import com.tabdeck.app.model.BrowserId
import com.tabdeck.app.model.TabItem
import com.tabdeck.app.model.TransferEvent
import com.tabdeck.app.model.TransferPacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class BrowserTransferCoordinator(private val context: Context) {
    data class Progress(
        val target: BrowserId,
        val total: Int,
        val processed: Int,
        val opened: Int,
        val failed: Int,
        val currentTitle: String = "",
        val currentUrl: String = "",
        val cancelled: Boolean = false,
    ) {
        val fraction: Float get() = if (total == 0) 0f else processed.toFloat() / total
    }

    data class Result(
        val event: TransferEvent,
        val failedUrls: List<String>,
        val successfulIds: Set<String>,
    )

    fun isInstalled(browser: BrowserId): Boolean {
        val packageName = browser.packageName ?: return false
        return runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    suspend fun transfer(
        tabs: List<TabItem>,
        target: BrowserId,
        pacing: TransferPacing,
        onProgress: (Progress) -> Unit = {},
    ): Result {
        val startedAt = System.currentTimeMillis()
        val packageName = target.packageName
        if (packageName == null || !isInstalled(target)) {
            val event = TransferEvent(
                targetBrowser = target,
                attempted = tabs.size,
                opened = 0,
                failed = tabs.size,
                durationMs = System.currentTimeMillis() - startedAt,
            )
            return Result(event, tabs.map { it.url }, emptySet())
        }

        var opened = 0
        var processed = 0
        var cancelled = false
        val failed = mutableListOf<String>()
        val successful = linkedSetOf<String>()

        try {
            for ((index, tab) in tabs.withIndex()) {
                coroutineContext.ensureActive()
                onProgress(Progress(target, tabs.size, processed, opened, failed.size, tab.title, tab.url))
                val success = openInBrowser(tab.url, packageName)
                processed++
                if (success) {
                    opened++
                    successful += tab.id
                } else {
                    failed += tab.url
                }
                onProgress(Progress(target, tabs.size, processed, opened, failed.size, tab.title, tab.url))
                if (index < tabs.lastIndex) delay(pacing.delayMs)
            }
        } catch (_: CancellationException) {
            cancelled = true
        }

        val event = TransferEvent(
            targetBrowser = target,
            attempted = processed,
            opened = opened,
            failed = failed.size,
            cancelled = cancelled,
            durationMs = System.currentTimeMillis() - startedAt,
        )
        onProgress(Progress(target, tabs.size, processed, opened, failed.size, cancelled = cancelled))
        return Result(event, failed, successful)
    }

    private fun openInBrowser(url: String, packageName: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addCategory(Intent.CATEGORY_BROWSABLE)
            putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
            putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
        }
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: RuntimeException) {
        false
    }

}
