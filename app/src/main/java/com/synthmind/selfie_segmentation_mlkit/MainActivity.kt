package com.synthmind.selfie_segmentation_mlkit


import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var btnLoadImage: Button
    private lateinit var btnRemoveBackground: Button
    private lateinit var ivOriginalImage: ImageView
    private lateinit var ivProcessedImage: ImageView

    private var originalBitmap: Bitmap? = null
//    private val PERMISSION_REQUEST_CODE = 100

    // Khởi tạo đối tượng segmenter từ ML Kit
    private val segmenterOptions = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
        .build()
    private val segmenter = Segmentation.getClient(segmenterOptions)

    // Định nghĩa launcher để chọn ảnh từ thư viện
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    loadImageFromUri(uri)
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Không thể tải ảnh", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Khởi tạo các thành phần UI
        btnLoadImage = findViewById(R.id.btnLoadImage)
        btnRemoveBackground = findViewById(R.id.btnRemoveBackground)
        ivOriginalImage = findViewById(R.id.ivOriginalImage)
        ivProcessedImage = findViewById(R.id.ivProcessedImage)

        btnLoadImage.setOnClickListener {
            openGallery()
        }
        // Thiết lập sự kiện click cho nút tách nền
        btnRemoveBackground.setOnClickListener {
            originalBitmap?.let {
                processImageWithSegmentation(it)
            } ?: run {
                Toast.makeText(this, "Hãy chọn một ảnh trước", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun loadImageFromUri(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        originalBitmap = BitmapFactory.decodeStream(inputStream)
        ivOriginalImage.setImageBitmap(originalBitmap)
        ivProcessedImage.setImageBitmap(null)
    }

    private fun processImageWithSegmentation(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)

        segmenter.process(image)
            .addOnSuccessListener { segmentationMask ->
                // Lấy mặt nạ phân đoạn (mask)
                val mask = segmentationMask.buffer
                val maskWidth = segmentationMask.width
                val maskHeight = segmentationMask.height

                // Đảm bảo kích thước mặt nạ khớp với kích thước bitmap
                val scaledBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    maskWidth,
                    maskHeight,
                    true
                )

                // Tạo bitmap kết quả với chế độ ARGB_8888 để hỗ trợ độ trong suốt
                val resultBitmap = Bitmap.createBitmap(
                    maskWidth, maskHeight, Bitmap.Config.ARGB_8888
                )

                // Xử lý từng pixel để tách nền
                val pixels = IntArray(maskWidth * maskHeight)
                scaledBitmap.getPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

                mask.rewind()
                for (y in 0 until maskHeight) {
                    for (x in 0 until maskWidth) {
                        val foregroundConfidence = mask.float
                        val pixelIndex = y * maskWidth + x
                        val pixel = pixels[pixelIndex]

                        // Nếu độ tin cậy cao, giữ nguyên pixel; ngược lại làm trong suốt
                        if (foregroundConfidence > 0.5) {
                            resultBitmap.setPixel(x, y, pixel)
                        } else {
                            // Làm trong suốt (alpha = 0)
                            resultBitmap.setPixel(x, y, 0)
                        }
                    }
                }

                // Hiển thị kết quả
                ivProcessedImage.setImageBitmap(resultBitmap)
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Lỗi khi xử lý ảnh: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}