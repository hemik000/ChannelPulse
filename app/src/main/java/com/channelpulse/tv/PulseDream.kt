package com.channelpulse.tv

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.dreams.DreamService
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.channelpulse.tv.ui.Pulse
import com.channelpulse.tv.ui.PulseTheme
import com.channelpulse.tv.ui.components.ScreenCare
import com.channelpulse.tv.ui.screens.LiveWallScreen

/**
 * The live wall as the TV's screensaver. Any remote button ends it, like every screensaver.
 * DreamService isn't a lifecycle owner, so it provides one for Compose itself.
 */
class PulseDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val container: AppContainer
        get() = (application as PulseApp).container

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val view = ComposeView(this).apply {
            setContent { PulseTheme { DreamWall(container) } }
        }
        setContentView(view)
        // Compose looks up these owners from the window's root view.
        listOf(view, window.decorView).forEach {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        registry.currentState = Lifecycle.State.RESUMED
        container.repo.start()
    }

    override fun onDreamingStopped() {
        container.repo.stop()
        registry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
}

@Composable
private fun DreamWall(container: AppContainer) {
    val channels by container.repo.channels.collectAsState()
    val status by container.repo.status.collectAsState()
    val settings by container.settings.state.collectAsState()
    ScreenCare(
        shift = settings.pixelShift,
        dimStart = settings.nightDimStart,
        dimEnd = settings.nightDimEnd,
        lastInputAt = 0L,
        // Fills the edges uncovered when burn-in protection shifts the content.
        modifier = Modifier.background(Pulse.bg),
    ) {
        LiveWallScreen(channels = channels, startId = null, cycleSeconds = settings.wallCycleSeconds, status = status)
    }
}

/**
 * Switches the screensaver on and off. The [PulseDream] component is enabled or disabled, and when
 * the app may write secure settings (granted once over adb) it also selects or unselects it as the
 * TV's screensaver, since Google TV's settings don't list other apps' screensavers.
 */
object Screensaver {
    /** One-time command that lets the toggle select the screensaver itself. */
    const val GRANT_COMMAND = "adb shell pm grant com.channelpulse.tv android.permission.WRITE_SECURE_SETTINGS"

    private const val KEY_COMPONENTS = "screensaver_components"
    private const val KEY_ENABLED = "screensaver_enabled"
    private const val PREFS = "screensaver"
    private const val KEY_PREVIOUS = "previous"

    private fun component(context: Context) = ComponentName(context, PulseDream::class.java)

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /** Whether the TV currently uses the live wall as its screensaver. */
    fun isSelected(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, KEY_COMPONENTS)
            ?.contains(component(context).flattenToString()) == true

    /** Whether the toggle can pick the TV's screensaver itself. */
    fun canSelect(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * On: enables the screensaver and, when allowed, makes it the TV's screensaver.
     * Off: hands the TV back the screensaver it had before and hides this one.
     */
    fun setEnabled(context: Context, on: Boolean) {
        if (on) {
            setComponent(context, true)
            select(context)
        } else {
            unselect(context)
            setComponent(context, false)
        }
    }

    private fun setComponent(context: Context, on: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun select(context: Context) {
        if (!canSelect(context) || isSelected(context)) return
        val resolver = context.contentResolver
        try {
            val previous = Settings.Secure.getString(resolver, KEY_COMPONENTS).orEmpty()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PREVIOUS, previous).apply()
            Settings.Secure.putString(resolver, KEY_COMPONENTS, component(context).flattenToString())
            Settings.Secure.putInt(resolver, KEY_ENABLED, 1)
        } catch (ignored: SecurityException) {
        }
    }

    private fun unselect(context: Context) {
        if (!canSelect(context) || !isSelected(context)) return
        val previous = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PREVIOUS, "").orEmpty()
        try {
            // An empty value makes the TV fall back to its default screensaver.
            Settings.Secure.putString(context.contentResolver, KEY_COMPONENTS, previous)
        } catch (ignored: SecurityException) {
        }
    }

    /** Opens the TV's screensaver picker; false if this TV doesn't have one. */
    fun openSystemSettings(context: Context): Boolean = try {
        context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
