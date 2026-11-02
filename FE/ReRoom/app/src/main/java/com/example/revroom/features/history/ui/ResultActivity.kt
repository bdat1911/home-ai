package com.example.revroom.features.history.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.revroom.R
import com.example.revroom.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kích hoạt chế độ hiển thị tràn viền (Edge-to-Edge)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Xử lý Insets để tránh Notch đè lên Toolbar và Navigation Bar đè lên ảnh
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.layoutAppBar.setPadding(bars.left, bars.top, bars.right, 0)
            binding.imgFullPreview.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }

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
