package com.channelpulse.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.channelpulse.tv.ui.PulseRoot
import com.channelpulse.tv.ui.PulseTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as PulseApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the TV awake so the dashboard can run as a wall display.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            PulseTheme {
                PulseRoot(container)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.repo.start()
        container.server.start()
    }

    override fun onStop() {
        // Stop polling while the app isn't visible so API quota isn't spent on an empty room.
        container.repo.stop()
        container.server.stop()
        super.onStop()
    }
}
