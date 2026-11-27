package io.github.princejoseph.meld

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/**
 * Cold-starts the real launcher activity, the way tapping the app icon does. Covers both a build
 * without cloud config and a release-like build where Firebase is initialised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28, 33, 35, 36])
class AppLaunchTest {

    private fun launch(intent: Intent? = null) {
        val controller = if (intent != null) Robolectric.buildActivity(MainActivity::class.java, intent)
        else Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.setup().get()
        repeat(5) { shadowOf(Looper.getMainLooper()).idle() }
        assertFalse("MainActivity finished during launch", activity.isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun launches_withoutFirebase() = launch()

    @Test
    fun launches_withFirebaseConfigured() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            FirebaseApp.initializeApp(
                ctx,
                FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("AIzaSyTEST_KEY_FOR_ROBOLECTRIC_00000000")
                    .setProjectId("demo-meld")
                    .build(),
            )
        }
        launch()
    }

    @Test
    fun showsSavedCrashReport_insteadOfClosing() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        java.io.File(ctx.filesDir, "last_crash.txt").writeText("Meld test\njava.lang.IllegalStateException: boom")
        launch()
        org.junit.Assert.assertNotNull(CrashReport.pending(ctx))
        CrashReport.clear(ctx)
    }

    @Test
    fun launches_fromRoomLink() = launch(Intent(Intent.ACTION_VIEW, Uri.parse("meld://r/K7Q2MZ")))
}
