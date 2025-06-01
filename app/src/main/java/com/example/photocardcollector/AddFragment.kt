package com.example.photocardcollector

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.core.content.FileProvider
import java.io.File
import java.util.Date
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.net.toFile
import androidx.core.net.toUri

class AddFragment : Fragment() {
    private lateinit var tmpImageUri: Uri

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    private val takeAndCropPictureLauncher = registerForActivityResult(TakeAndCropPicture()) { onPhotoToken() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.add_button).setOnClickListener {
            if (checkCameraPermission()) {
                takeAndCropPictureLauncher.launch(
                    createTmpImageFile().also {
                        tmpImageUri = it
                    }
                )
            } else {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createTmpImageFile(): Uri {
        val folder = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return File(folder, "tmp.jpg").also {
            it.createNewFile()
        }.toUri()
    }

    private fun onPhotoToken () {
        if (!isPhotoDuplicate()) {
            showAddPhotoDialog()
        } else {
            Toast.makeText(context, "Already Existed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPhotoDuplicate(): Boolean {
        val storedFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyCards")
        return storedFolder.listFiles()?.any{
            val similarity = Phash(tmpImageUri.toFile().path, it.path).getSimilarityScore()
            println("compare to ${it.path}, similarity: $similarity")
            similarity > 80
        }?:false
    }

    private fun showAddPhotoDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Add Photo")
            .setMessage("Add the card to My Cards?")
            .setPositiveButton("OK") { _, _ ->
                savePhoto()
            }
            .show()
    }

    private fun savePhoto() {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyCards")
        if(!folder.exists()){
            folder.mkdirs()
        }

        val destination = File(folder, "${System.currentTimeMillis()}.jpg")
        if(!destination.exists()){
            destination.createNewFile()
        }
        tmpImageUri.toFile().run {
            copyTo(destination, true)
            delete()
        }
    }

    private class TakeAndCropPicture: ActivityResultContract<Uri, Boolean>() {
        override fun createIntent(context: Context, input: Uri): Intent {
            return Intent(context, ActivityTakePicture::class.java).
                putExtra(MediaStore.EXTRA_OUTPUT, input)
        }
        override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
            return resultCode == Activity.RESULT_OK
        }
    }

    private class Phash(
        fileOnePath: String?,
        fileTwoPath: String?,
        bitSize: Int = 8
    ) {
        private var bitMapOne: Bitmap? = null
        private var bitMapTwo: Bitmap? = null
        private var hashOne: String? = null
        private var hashTwo: String? = null
        private var hDistance: Int = 101

        init {
            // First we are converting our image to 8x8 bits
            bitMapOne = resizeToNxN(fileOnePath, bitSize)
            bitMapTwo = resizeToNxN(fileTwoPath, bitSize)
            // Then converting bitmap to grayscale
            bitMapOne = bitMapOne?.let { toGreyscale(it) }
            bitMapTwo = bitMapTwo?.let { toGreyscale(it) }
            // Getting the hash from the images
            hashOne = bitMapOne?.let { buildHash(it) }
            hashTwo = bitMapTwo?.let { buildHash(it) }
            // Finally getting hamming distance
            hDistance = getHammingDistance(hashOne.toString(), hashTwo.toString())
        }

        fun getSimilarityScore(): Int = 100 - hDistance

        private fun getHammingDistance(one: String, two: String): Int {
            if (one.length != two.length) {
                return -1
            }
            var counter = 0
            for (i in one.indices) {
                if (one[i] != two[i]) counter++
            }
            return counter
        }

        private fun resizeToNxN(filePath: String?, n: Int): Bitmap? =
            BitmapFactory.decodeFile(filePath)?.scale(n, n)

        private fun toGreyscale(bmpOriginal: Bitmap): Bitmap {
            val height: Int = bmpOriginal.height
            val width: Int = bmpOriginal.width
            val bmpGrayscale = createBitmap(width, height)
            val canvas = Canvas(bmpGrayscale)
            val ma = ColorMatrix()
            ma.setSaturation(0f)
            val paint = Paint()
            paint.colorFilter = ColorMatrixColorFilter(ma)
            canvas.drawBitmap(bmpOriginal, 0f, 0f, paint)
            return bmpGrayscale
        }

        private fun buildHash(grayscaleBitmap: Bitmap): String {
            val myHeight = grayscaleBitmap.height
            val myWidth = grayscaleBitmap.width
            var totalPixVal = 0
            for (i in 0 until myWidth) {
                for (j in 0 until myHeight) {
                    val currPixel = grayscaleBitmap[i, j] and 0xff //read lowest byte of pixels
                    totalPixVal += currPixel
                }
            }
            val average = totalPixVal / 64
            var hashVal = ""
            for (i in 0 until myWidth) {
                for (j in 0 until myHeight) {
                    val currPixel = grayscaleBitmap[i, j] and 0xff //read lowest byte of pixels
                    hashVal += if (currPixel >= average) {
                        "1"
                    } else {
                        "0"
                    }
                }
            }
            return hashVal
        }

    }
}
