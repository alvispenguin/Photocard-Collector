package com.example.photocardcollector

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.View
import android.widget.RelativeLayout
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
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
import java.io.FileOutputStream

class ActivityTakePicture: AppCompatActivity() {
    companion object {
        private const val CARD_RATIO = 11.0 / 17.0
        private const val INDICATOR_HEIGHT_RATIO = 0.9
    }

    private lateinit var outputUri: Uri
    private lateinit var rootBinding: ActivityTakePictureBinding
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var camera: Camera
    private lateinit var imageCapture: ImageCapture
    private lateinit var indicatorView: IndicatorView

    private var processing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        outputUri = intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)!!

        rootBinding = ActivityTakePictureBinding.inflate(layoutInflater)

        rootBinding.previewView.apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        rootBinding.captureButton.setOnClickListener {
            if (processing) {
                return@setOnClickListener
            }
            processing = true

            rootBinding.captureButton.visibility = View.INVISIBLE
            rootBinding.backButton.visibility = View.INVISIBLE
            rootBinding.previewView.visibility = View.INVISIBLE
            indicatorView.visibility = View.INVISIBLE
            rootBinding.progress.root.visibility = View.VISIBLE

            takePicture { uri ->
                cropPicture(uri)
                setResult(
                    Activity.RESULT_OK,
                    Intent().apply {
                        putExtra("uri", uri)
                    }
                )
                finish()
            }
        }
        rootBinding.backButton.setOnClickListener {
            if (processing) {
                return@setOnClickListener
            }
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        rootBinding.progress.textView.text = getString(R.string.processing)

        setContentView(rootBinding.root)

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this).apply {
            addListener(
                {
                    val cameraProvider = get()
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()
                    val preview = Preview.Builder().build().apply {
                        surfaceProvider = rootBinding.previewView.surfaceProvider
                    }

                    camera = cameraProvider.bindToLifecycle(
                        this@ActivityTakePicture as LifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    val size = getCameraSize(camera)
                    val height = size.width * resources.displayMetrics.widthPixels / size.height * INDICATOR_HEIGHT_RATIO
                    val width = height * CARD_RATIO
                    rootBinding.cameraWrapper.addView(
                        IndicatorView(
                            this@ActivityTakePicture,
                            height.toInt(), width.toInt()
                        ).also {
                            indicatorView = it
                        }
                    )
                },
                ContextCompat.getMainExecutor(this@ActivityTakePicture)
            )
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun getCameraSize (camera: Camera): Size {
        val cameraId = Camera2CameraInfo.from(camera.cameraInfo).cameraId
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cs = cm.getCameraCharacteristics(cameraId)
        val m = cs.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        return m.getOutputSizes(SurfaceTexture::class.java)!!.maxByOrNull { it.height }!!
    }

    private fun takePicture (cb: (Uri) -> Unit) {
        println("[${this::class.simpleName}.${this::takePicture.name}]")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(outputUri.toFile()).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    outputFileResults.savedUri?.let { cb(it) }
                }
                override fun onError(exception: ImageCaptureException) = throw exception
            }
        )
    }

    private fun cropPicture (savedUri: Uri) {
        println("[${this::class.simpleName}.${this::cropPicture.name}]")

        val bitmap = BitmapFactory.decodeFile(savedUri.path)

        val width = bitmap.width * INDICATOR_HEIGHT_RATIO
        val height = width * CARD_RATIO
        val cropped = Bitmap.createBitmap(
            bitmap,
            ((bitmap.width - width) / 2).toInt(),
            ((bitmap.height - height) / 2).toInt(),
            width.toInt(),
            height.toInt(),
            Matrix().apply { postRotate(90f) },
            true
        )

        FileOutputStream(savedUri.toFile()).use {
            cropped.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private inner class IndicatorView (context: Context, height: Int, width: Int): View(context) {
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
            println("[${this::class.simpleName}.${this::onDraw.name}] height:$height width:$width")
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }
}