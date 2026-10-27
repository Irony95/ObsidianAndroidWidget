package com.example.obsidianandroidwidgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class WidgetUpgradeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WorkManager.getInstance(context).enqueueUniqueWork("refresh-widgets-after-upgrade",
                ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<UpdateWidgetWorker>()
                    .setInputData(workDataOf("force" to true)).build())
        }
    }
}
