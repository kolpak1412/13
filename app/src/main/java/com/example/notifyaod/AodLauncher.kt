package com.example.notifyaod

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** Запускает экран-«AOD» из фона. Вызывать из главного потока. */
object AodLauncher {
    private val handler = Handler(Looper.getMainLooper())

    fun show(context: Context, pkgs: List<String>) {
        val app = context.applicationContext
        val wm = app.getSystemService(WindowManager::class.java)

        // Невидимое окно 1x1: на новых версиях Android разрешение «поверх других приложений»
        // помогает запускать Activity из фона только пока у приложения есть видимое окно.
        var anchor: View? = null
        if (Settings.canDrawOverlays(app)) {
            try {
                val view = View(app)
                val lp = WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )
                lp.gravity = Gravity.TOP or Gravity.START
                wm.addView(view, lp)
                anchor = view
            } catch (e: Exception) {
                Log.w(TAG, "Cannot add anchor overlay", e)
            }
        }

        val intent = Intent(app, AodActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putStringArrayListExtra(AodActivity.EXTRA_PACKAGES, ArrayList(pkgs))
        try {
            app.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot start AodActivity", e)
        }

        anchor?.let { view ->
            handler.postDelayed({
                try { wm.removeView(view) } catch (_: Exception) { }
            }, 2000L)
        }
    }

    private const val TAG = "AodLauncher"
}
