package com.example.revroom.features.history.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.revroom.R
import com.example.revroom.core.utils.DateFormatter
import com.example.revroom.databinding.ActivityProjectDetailBinding
import com.example.revroom.features.history.viewmodel.HistoryViewModel
import com.google.android.material.snackbar.Snackbar

class ProjectDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectDetailBinding
    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModel.Factory(this)
    }

    private var designId: String? = null
    private var originalImageUrl: String? = null
    private var designedImageUrl: String? = null
    private var status: String? = null
    private var createdAt: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Kích hoạt chế độ hiển thị tràn viền (Edge-to-Edge)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityProjectDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Xử lý Insets để tránh Notch đè lên Header và Navigation Bar đè lên Nút bấm
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Đẩy Toolbar xuống dưới vùng Notch
            binding.layoutAppBar.setPadding(0, systemBars.top, 0, 0)
            
            // Thêm khoảng trống phía dưới NestedScrollView để tránh bị thanh điều hướng che mất nút Delete
            binding.nestedScrollView.setPadding(0, 0, 0, systemBars.bottom)

            insets
        }

        // Đọc dữ liệu từ Intent
        intent?.let {
            designId = it.getStringExtra(EXTRA_DESIGN_ID)
            originalImageUrl = it.getStringExtra(EXTRA_ORIGINAL_IMAGE)
            designedImageUrl = it.getStringExtra(EXTRA_DESIGNED_IMAGE)
            status = it.getStringExtra(EXTRA_STATUS)
            createdAt = it.getStringExtra(EXTRA_CREATED_AT)
        }

        setupViews()
        setupListeners()
        observeViewModel()
    }

    private fun setupViews() {
        // Load Original image
        Glide.with(this)
                .load(originalImageUrl)
                .placeholder(R.drawable.ic_gallery_empty)
                .error(R.drawable.ic_gallery_empty)
                .into(binding.imgOriginal)

        // Load Designed outcome (fall back to original if designed image is not generated yet)
        val displayOutcome = if (designedImageUrl.isNullOrEmpty()) originalImageUrl else designedImageUrl
        Glide.with(this)
                .load(displayOutcome)
                .placeholder(R.drawable.ic_gallery_empty)
                .error(R.drawable.ic_gallery_empty)
                .into(binding.imgDesigned)

        // Bind metadata
        binding.txtProjectId.text = designId ?: "N/A"
        binding.txtCreatedDate.text = DateFormatter.formatToDayMonthYear(createdAt)

        // Set status and status pill design
        binding.txtStatus.text = status ?: "Pending"
        when {
            "Completed".equals(status, ignoreCase = true) -> {
                binding.txtStatus.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_status_completed))
            }
            "Failed".equals(status, ignoreCase = true) -> {
                binding.txtStatus.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_status_failed))
            }
            else -> {
                binding.txtStatus.text = "Processing"
                binding.txtStatus.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_status_pending))
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnViewAgain.setOnClickListener {
            navigateToResult()
        }

        binding.btnDelete.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(this)
                .setTitle("Delete Project")
                .setMessage("Are you sure you want to permanently delete this project? This action cannot be undone.")
                .setPositiveButton("Yes, Delete") { dialog, _ ->
                    dialog.dismiss()
                    designId?.let { id ->
                        viewModel.deleteProject(id)
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
    }

    private fun observeViewModel() {
        // Observe deletion progress
        viewModel.isDeleting.observe(this) { isDeleting ->
            if (isDeleting) {
                binding.layoutDeleteOverlay.visibility = View.VISIBLE
            } else {
                binding.layoutDeleteOverlay.visibility = View.GONE
            }
        }

        // Observe deletion success
        viewModel.deleteSuccess.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Project deleted successfully", Toast.LENGTH_SHORT).show()
                // Set the result back to HistoryScreen so it can remove this project from its local list
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_DELETED_ID, designId)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }

        // Observe deletion error
        viewModel.deleteError.observe(this) { error ->
            if (error != null) {
                Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToResult() {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra(EXTRA_DESIGN_ID, designId)
            putExtra(EXTRA_DESIGNED_IMAGE, designedImageUrl ?: originalImageUrl)
            putExtra(EXTRA_ORIGINAL_IMAGE, originalImageUrl)
        }
        startActivity(intent)
    }

    companion object {
        const val EXTRA_DELETED_ID = "extra_deleted_id"
        const val EXTRA_DESIGN_ID = "extra_design_id"
        const val EXTRA_ORIGINAL_IMAGE = "extra_original_image"
        const val EXTRA_DESIGNED_IMAGE = "extra_designed_image"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_CREATED_AT = "extra_created_at"
    }
}
