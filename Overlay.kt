package com.axter.opu

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import java.io.File

/**
 * Opu on top of every app. Only the character's own box catches touches,
 * so the rest of the screen stays usable. Double-tap = send her away. Drag = move her.
 */
class Overlay(private val ctx: Context, private val onDismiss: () -> Unit, private val onTap: () -> Unit) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private var web: WebView? = null
    private var lp: WindowManager.LayoutParams? = null
    val visible get() = root != null

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    fun show() {
        if (root != null) return
        val d = ctx.resources.displayMetrics
        val w = (d.density * 260).toInt()
        val h = (d.density * 380).toInt()
        val p = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        p.x = d.widthPixels - w - (d.density * 8).toInt()
        p.y = d.heightPixels - h - (d.density * 24).toInt()
        lp = p

        val loader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
            .addPathHandler("/model/", WebViewAssetLoader.InternalStoragePathHandler(ctx, File(ctx.filesDir, "model").apply { mkdirs() }))
            .build()

        val wv = WebView(ctx)
        wv.setBackgroundColor(Color.TRANSPARENT)
        wv.settings.javaScriptEnabled = true
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(r.url)
        }
        wv.loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
        web = wv

        val box = FrameLayout(ctx)
        box.addView(wv, FrameLayout.LayoutParams(-1, -1))
        // WebView never sees touches; the gesture detector below handles them.
        val shield = View(ctx)
        box.addView(shield, FrameLayout.LayoutParams(-1, -1))

        var sx = 0f; var sy = 0f; var px = 0; var py = 0
        val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { onDismiss(); return true }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onTap(); return true }
            override fun onDown(e: MotionEvent) = true
        })
        shield.setOnTouchListener { _, e ->
            gd.onTouchEvent(e)
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; px = p.x; py = p.y }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - sx) + Math.abs(e.rawY - sy) > 24) {
                        p.x = px + (e.rawX - sx).toInt(); p.y = py + (e.rawY - sy).toInt()
                        runCatching { wm.updateViewLayout(box, p) }
                    }
                }
            }
            true
        }
        root = box
        wm.addView(box, p)
    }

    fun state(s: String) { web?.post { web?.evaluateJavascript("window.setState && setState('$s')", null) } }

    fun hide() {
        val r = root ?: return
        runCatching { wm.removeView(r) }
        web?.destroy()
        web = null; root = null
    }
}
