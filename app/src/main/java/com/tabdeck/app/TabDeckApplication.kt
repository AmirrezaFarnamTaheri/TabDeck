package com.tabdeck.app

import android.app.Application
import com.tabdeck.app.data.TabDeckRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TabDeckApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository: TabDeckRepository by lazy { TabDeckRepository(this) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            repository.initialize()
            MaintenanceWorker.reconcileSchedule(
                this@TabDeckApplication,
                repository.currentState().settings.automaticMaintenanceEnabled,
            )
        }
    }
}
