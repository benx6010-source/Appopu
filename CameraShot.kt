package com.axter.opu

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

/**
 * "Look at me": opens invisibly for about a second, takes one front-camera photo, closes.
 * Nothing is saved to the gallery; the photo only goes to Gemini for that one question.
 */
class CameraShot : AppCompatActivity() {

    companion object {
        private var pending: CompletableDeferred<String?>? = null

        suspend fun capture(ctx: android.content.Context): String? {
            val d = CompletableDeferred<String?>()
            pending = d
            ctx.startActivity(android.content.Intent(ctx, CameraShot::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return withTimeoutOrNull(8000) { d.await() }.also { pending = null }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            try {
                val provider = fut.get()
                val cap = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, cap)
                window.decorView.postDelayed({
                    cap.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(img: ImageProxy) {
                            val out = toJpeg(img)
                            img.close()
                            pending?.complete(out)
                            finish()
                        }
                        override fun onError(e: ImageCaptureException) { pending?.complete(null); finish() }
                    })
                }, 700) // let the camera adjust exposure
            } catch (e: Exception) { pending?.complete(null); finish() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toJpeg(img: ImageProxy): String? = try {
        val buf = img.planes[0].buffer
        val bytes = ByteArray(buf.remaining()); buf.get(bytes)
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val m = Matrix().apply { postRotate(img.imageInfo.rotationDegrees.toFloat()) }
        bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val w = 640
        val s = android.graphics.Bitmap.createScaledBitmap(bmp, w, (bmp.height * w.toFloat() / bmp.width).toInt(), true)
        val o = ByteArrayOutputStream()
        s.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, o)
        Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) { null }
}
