package com.channelpulse.tv

import android.app.Application
import android.content.Context
import com.channelpulse.tv.data.HistoryStore
import com.channelpulse.tv.data.KeyPool
import com.channelpulse.tv.data.PulseRepository
import com.channelpulse.tv.data.SettingsStore
import com.channelpulse.tv.data.YouTubeApi
import com.channelpulse.tv.server.CompanionServer
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class PulseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Creates and holds the app's single instances. */
class AppContainer(context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val settings = SettingsStore(context)
    val keyPool = KeyPool(context.getSharedPreferences("keys", Context.MODE_PRIVATE))
    private val history = HistoryStore(File(context.filesDir, "history.json"))
    val repo = PulseRepository(YouTubeApi(http), settings, keyPool, history)
    val server = CompanionServer(repo, settings, keyPool)
}
