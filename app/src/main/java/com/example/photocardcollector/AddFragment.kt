package com.example.photocardcollector

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.icu.text.SimpleDateFormat
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.core.content.FileProvider
import java.io.File
import java.util.Date

class AddFragment : Fragment() {
    private lateinit var photoFile: File
    private val CAMERA_REQUEST_CODE = 1000

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
                openCamera()
            } else {
                requestCameraPermission()
            }
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_REQUEST_CODE
        )
    }

    private fun openCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { intent ->
            intent.resolveActivity(requireActivity().packageManager)?.also {
                photoFile = createImageFile()
                photoFile.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.provider",
                        it
                    )
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(intent, CAMERA_REQUEST_CODE)
                }
            }
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir = File(
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "MyCards"
        )
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (!isPhotoDuplicate()) {
                showAddPhotoDialog()
            }
            else{
                Toast.makeText(context, "Already Existed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isPhotoDuplicate(): Boolean {
        val storedFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyCards")
        return storedFolder.listFiles()?.any{
            val similarity = Phash(photoFile.path, it.path).getSimilarityScore()
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
        val folder=File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"MyCards")
        if(!folder.exists()){
            folder.mkdirs()
        }
        val destination = File(
            folder,
            photoFile.name
        )
        if(!destination.exists()){
            destination.createNewFile()
        }
        photoFile.copyTo(destination, true)
    }

    class Phash(
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

        fun getSimilarityScore(): Int {
            return 100 - hDistance
        }

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

        private fun resizeToNxN(filePath: String?, N: Int): Bitmap? {
            val originalBitmap = BitmapFactory.decodeFile(filePath) ?: return null
            return Bitmap.createScaledBitmap(originalBitmap, N, N, true)
        }

        private fun toGreyscale(bmpOriginal: Bitmap): Bitmap? {
            val height: Int = bmpOriginal.height
            val width: Int = bmpOriginal.width
            val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
                    val currPixel = grayscaleBitmap.getPixel(i, j) and 0xff //read lowest byte of pixels
                    totalPixVal += currPixel
                }
            }
            val average = totalPixVal / 64
            var hashVal = ""
            for (i in 0 until myWidth) {
                for (j in 0 until myHeight) {
                    val currPixel = grayscaleBitmap.getPixel(i, j) and 0xff //read lowest byte of pixels
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
