package com.example.photocardcollector

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import androidx.core.view.isVisible

class MyCardsFragment : Fragment() {
    private var isLatestFirst = true
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PhotoAdapter
    private lateinit var faq: CardView
    private lateinit var deletebutton: Button

    private var deletemode=false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mycards, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        adapter = PhotoAdapter()
        recyclerView.adapter = adapter
        loadPhotos()

        faq = view.findViewById(R.id.faq)
        deletebutton = view.findViewById(R.id.delete)
        faq.setOnClickListener {
            if (deletebutton.isVisible){
                deletebutton.visibility=View.INVISIBLE
            }
            else{
                deletebutton.visibility=View.VISIBLE
            }
        }
        deletebutton.setOnClickListener {
            deletemode=!deletemode
            (recyclerView.adapter as PhotoAdapter).notifyDataSetChanged()
            deletebutton.text=if (deletemode)"Done" else "Delete"
            if (!deletemode){
                deletebutton.visibility=View.INVISIBLE
            }
        }
    }

    private fun loadPhotos() {
        val photoDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "MyCards"
        )
        if (photoDir.exists()) {
            val photos = photoDir.listFiles()?.toList() ?: emptyList()
            val sortedPhotos = if (isLatestFirst) {
                photos.sortedByDescending { it.lastModified() }
            } else {
                photos.sortedBy { it.lastModified() }
            }
            adapter.submitList(sortedPhotos)
        }
    }
    private fun showDeleteDialog(file: File) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Photo")
            .setMessage("Are you sure you want to delete this photo?")
            .setPositiveButton("Delete") { dialog, _ ->
                deletePhoto(file)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deletePhoto(file: File) {
        if (file.exists()) {
            if (file.delete()) {
                // Show success message
                Toast.makeText(context, "Photo deleted", Toast.LENGTH_SHORT).show()
                // Reload the photos list
                loadPhotos()
            } else {
                // Show error message if deletion fails
                Toast.makeText(context, "Failed to delete photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleSort() {
        isLatestFirst = !isLatestFirst
        loadPhotos()
    }

    inner class PhotoAdapter : ListAdapter<File, PhotoAdapter.PhotoViewHolder>(PhotoDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_photo, parent, false)
            return PhotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imageView: ImageView = itemView.findViewById(R.id.imageView)
            private val selected: Button=itemView.findViewById(R.id.selected)

            fun bind(file: File) {
                Glide.with(itemView.context)
                    .load(file)
                    .into(imageView)
                selected.visibility=if(deletemode)View.VISIBLE else View.INVISIBLE
                selected.setOnClickListener {
                    showDeleteDialog(file)
                }
            }
        }
    }
    inner class PhotoDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.lastModified() == newItem.lastModified()
        }
    }
}