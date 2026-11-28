package io.github.princejoseph.meld

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import io.github.princejoseph.meld.ui.components.MeldSplash
import io.github.princejoseph.meld.ui.theme.MeldAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Steps the launch splash through its timeline and checks it ends (optionally saving frames). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SplashFramesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun splashAnimatesAndFinishes() {
        var finished by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MeldAppTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    if (!finished) MeldSplash(onFinished = { finished = true })
                }
            }
        }
        val out = System.getProperty("meld.splashFramesDir")?.let { File(it).apply { mkdirs() } }
        var t = 0L
        for (ms in listOf(0L, 250L, 450L, 700L, 900L, 1050L, 1200L, 1330L)) {
            compose.mainClock.advanceTimeBy(ms - t); t = ms
            if (out != null && !finished) {
                val root = compose.activity.window.decorView
                val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                compose.runOnUiThread { root.draw(Canvas(bmp)) }
                File(out, "frame_%04d.png".format(ms)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        assertTrue("splash should finish", finished)
    }
}
