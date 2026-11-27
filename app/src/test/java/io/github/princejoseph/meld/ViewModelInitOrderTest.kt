package io.github.princejoseph.meld

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.princejoseph.meld.ui.MeldViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression for the v1.1.0–1.1.2 launch crash on Android 17: coroutines started in the
 * ViewModel's init ran immediately and read a property declared further down (still null).
 * An unconfined Main dispatcher runs them eagerly, like the device did.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ViewModelInitOrderTest {
    @Before fun eagerMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    @Test
    fun constructsWhenCoroutinesStartImmediately() {
        // On a phone an exception in viewModelScope.launch kills the app; in tests it only goes
        // to the thread's handler. Record it so the test fails the way the device did.
        val crashes = mutableListOf<Throwable>()
        val thread = Thread.currentThread()
        val previous = thread.uncaughtExceptionHandler
        val previousDefault = Thread.getDefaultUncaughtExceptionHandler()
        val record = Thread.UncaughtExceptionHandler { _, e -> crashes += e }
        thread.uncaughtExceptionHandler = record
        Thread.setDefaultUncaughtExceptionHandler(record)
        try {
            val vm = MeldViewModel(ApplicationProvider.getApplicationContext<Application>())
            vm.openRoom("K7Q2MZ") // exercises the active-room mirror started in init
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        } finally {
            thread.uncaughtExceptionHandler = previous
            Thread.setDefaultUncaughtExceptionHandler(previousDefault)
        }
        crashes.firstOrNull()?.let { throw AssertionError("Crashed during ViewModel init", it) }
    }
}
