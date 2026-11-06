package com.jpetrydesign.pyng

import android.app.Application

class PyngApp : Application() {
    lateinit var hub: Hub
        private set

    override fun onCreate() {
        super.onCreate()
        hub = Hub(filesDir.resolve("engine").absolutePath)
    }
}
