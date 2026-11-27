package io.github.princejoseph.meld

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Local-only crash capture (Meld has no analytics). If the app crashes, the stack trace is saved
 * on the device; on the next launch the user sees it and can choose to share it. Nothing is sent
 * anywhere automatically.
 */
class MeldApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReport.install(this)
    }
}

object CrashReport {
    private const val FILE = "last_crash.txt"

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
                file(app).writeText(
                    buildString {
                        appendLine("Meld ${info?.versionName ?: "?"} (${BuildConfig.BUILD_TYPE})")
                        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        append(trace.take(60_000))
                    }
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    /**
     * Plain-View screen (no Compose, so it works even if the UI layer is what crashed) that shows
     * the saved report with Share and Try again buttons.
     */
    fun showScreen(activity: Activity, report: String, onRetry: () -> Unit) {
        val dp = activity.resources.displayMetrics.density
        val pad = (20 * dp).toInt()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0B0D"))
            setPadding(pad, pad * 2, pad, pad)
            fitsSystemWindows = true
        }
        root.addView(TextView(activity).apply {
            text = "Meld closed unexpectedly"
            textSize = 22f
            setTextColor(Color.parseColor("#F4F4F6"))
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(activity).apply {
            text = "This report stays on your phone. Share it with the developer to get it fixed, then try again."
            textSize = 14f
            setTextColor(Color.parseColor("#C9C9D1"))
            setPadding(0, pad / 2, 0, pad / 2)
        })
        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START }
        buttons.addView(Button(activity).apply {
            text = "Share report"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Meld crash report")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                activity.startActivity(Intent.createChooser(send, "Share crash report"))
            }
        })
        buttons.addView(Button(activity).apply {
            text = "Try again"
            setOnClickListener { clear(activity); onRetry() }
        })
        root.addView(buttons)
        root.addView(ScrollView(activity).apply {
            addView(TextView(activity).apply {
                text = report
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#8E8E98"))
                setTextIsSelectable(true)
                setPadding(0, pad / 2, 0, 0)
            })
        }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        activity.setContentView(root, android.view.ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }
}
