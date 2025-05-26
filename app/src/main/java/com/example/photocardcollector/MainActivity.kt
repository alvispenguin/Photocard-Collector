package com.example.photocardcollector

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var btnAdd: Button
    private lateinit var btnMyCards: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnAdd = findViewById(R.id.btnAdd)
        btnMyCards = findViewById(R.id.btnMyCards)

        btnAdd.setOnClickListener {
            showAddFragment()
        }

        btnMyCards.setOnClickListener {
            showMyCardsFragment()
        }

//        btnSort.setOnClickListener {
//            val fragment = supportFragmentManager.findFragmentById(R.id.container)
//            if (fragment is MyCardsFragment) {
//                fragment.toggleSort()
//            }
//        }

        // Show Add fragment by default
        showAddFragment()
    }

    private fun showAddFragment() {
//        btnSort.visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, AddFragment())
            .commit()
    }

    private fun showMyCardsFragment() {
//        btnSort.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, MyCardsFragment())
            .commit()
    }
}