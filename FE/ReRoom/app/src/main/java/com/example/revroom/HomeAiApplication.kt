package com.example.revroom

import android.app.Application
import com.example.revroom.core.network.ServerEndpoint

class HomeAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServerEndpoint.initialize(this)
    }
}
