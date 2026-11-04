package nobility.downloader.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.settings.Defaults
import java.awt.GraphicsEnvironment
import java.beans.PropertyChangeListener

object UiScale {
    var percent by mutableStateOf(0)
        private set
    fun load() { percent = Defaults.UI_SCALE.int() }
    fun select(value: Int) { percent = value; Defaults.UI_SCALE.update(value) }

    /** Respect existing OS scaling; only fill the gap when automatic desktop scaling is missing. */
    @JvmStatic fun density(native: Float, pixelHeight: Int, percent: Int): Float {
        val base = native.coerceAtLeast(1f)
        if (percent != 0) return base * percent.coerceIn(100, 250) / 100f
        val target = when {
            pixelHeight >= 2000 -> 2f
            pixelHeight >= 1400 -> 1.25f
            else -> 1f
        }
        return maxOf(base, target)
    }

    fun windowSize(requested: DpSize): DpSize = runCatching {
        val config = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val native = config.defaultTransform.scaleX.toFloat()
        val factor = density(native, config.device.displayMode.height, percent) / native
        DpSize((requested.width.value * factor).coerceAtMost(config.bounds.width * .9f).dp,
            (requested.height.value * factor).coerceAtMost(config.bounds.height * .9f).dp)
    }.getOrDefault(requested)
}

@Composable
fun ScaledWindow(window: ComposeWindow, content: @Composable () -> Unit) {
    val native = LocalDensity.current
    var height by remember(window) { mutableIntStateOf(window.graphicsConfiguration.device.displayMode.height) }
    DisposableEffect(window) {
        val listener = PropertyChangeListener { height = window.graphicsConfiguration.device.displayMode.height }
        window.addPropertyChangeListener("graphicsConfiguration", listener)
        onDispose { window.removePropertyChangeListener("graphicsConfiguration", listener) }
    }
    val density = UiScale.density(native.density, height, UiScale.percent)
    CompositionLocalProvider(LocalDensity provides Density(density, native.fontScale), content = content)
}
