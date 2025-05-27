package com.example.photocardcollector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.lifecycle.LifecycleOwner
import com.example.photocardcollector.databinding.ActivityTakePictureBinding
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Date

class ActivityTakePicture: AppCompatActivity() {
    private lateinit var rootBinding: ActivityTakePictureBinding
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var camera: Camera
    private lateinit var imageCapture: ImageCapture

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootBinding = ActivityTakePictureBinding.inflate(layoutInflater)

        rootBinding.previewView.apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        rootBinding.captureButton.setOnClickListener {
            takePicture()
        }

        setContentView(rootBinding.root)

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this).apply {
            addListener(
                {
                    val cameraProvider = get()
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    val preview = Preview.Builder().build().apply {
                        surfaceProvider = rootBinding.previewView.surfaceProvider
                    }

                    camera = cameraProvider.bindToLifecycle(
                        this@ActivityTakePicture as LifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    preview.resolutionInfo!!.resolution.let { resolution ->
                        val height = (resolution.width * 0.8).toInt()
                        rootBinding.cameraWrapper.addView(
                            IndicatorView(
                                this@ActivityTakePicture,
                                height, (height * 11.0 / 17.0).toInt()
                            )
                        )
                    }
                },
                ContextCompat.getMainExecutor(this@ActivityTakePicture)
            )
        }
    }

    private fun takePicture () {
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(createImageFile()).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    outputFileResults.savedUri?.let {
                        cropPicture(it)
                    }
                }
                override fun onError(exception: ImageCaptureException) = throw exception
            }
        )
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat.getDateInstance().format(Date())
        val storageDir = File(
            baseContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "MyCards"
        )
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File(storageDir, "JPEG_${timeStamp}.jpg").also {
            it.createNewFile()
        }
    }

    private fun cropPicture (savedUri: Uri) {
        val bitmap = BitmapFactory.decodeFile(savedUri.path)

        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(90f) }, true
        )

        val height = rotated.height * 0.86
        val width = height * 11.0 / 17.0
        val cropped = Bitmap.createBitmap(
            rotated,
            ((rotated.width - width) / 2).toInt(),
            ((rotated.height - height) / 2).toInt(),
            width.toInt(),
            height.toInt(),
        )
        FileOutputStream(savedUri.toFile()).use {
            cropped.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private class IndicatorView (context: Context, height: Int, width: Int): View(context) {
        private val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 16f
        }

        init {
            layoutParams = RelativeLayout.LayoutParams(width, height).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }
}