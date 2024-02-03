package com.example.obsidianandroidwidgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.io.File


class FileSystemObserverService: Service() {
    companion object {
        const val PATH_EXTRA = "path_extra"
    }
    lateinit var mFileObserver : FileObserver

    override fun onBind(p0: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onCreate() {
        super.onCreate()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getStringExtra(PATH_EXTRA) != null)
        {
            Log.d("test", intent.getStringExtra(PATH_EXTRA) ?: "empty")
            val file = File(intent.getStringExtra(PATH_EXTRA))
            mFileObserver = Observer(file)
            mFileObserver.startWatching()
        }

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        Toast.makeText(this, "end", Toast.LENGTH_LONG).show()
        mFileObserver.stopWatching()
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    class Observer(file: File) : FileObserver(file, ALL_EVENTS)
    {
        override fun onEvent(p0: Int, p1: String?) {
            Log.d("test", "Event $p0 location $p1")
        }

    }
}

