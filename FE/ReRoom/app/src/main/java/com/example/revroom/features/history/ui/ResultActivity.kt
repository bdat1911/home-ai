package com.example.revroom.features.history.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.revroom.R
import com.example.revroom.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read design image URL from intent
        val designedImageUrl = intent?.getStringExtra(ProjectDetailActivity.EXTRA_DESIGNED_IMAGE)

        // Load the full preview image with Glide
        Glide.with(this)
                .load(designedImageUrl)
                .placeholder(R.drawable.ic_gallery_empty)
                .error(R.drawable.ic_gallery_empty)
                .into(binding.imgFullPreview)

        // Setup back button listener
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
