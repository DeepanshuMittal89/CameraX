package com.example.camerax

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.impl.CameraCaptureMetaData.FlashState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.camerax.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var imageCapture: ImageCapture
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var videoCapture: VideoCapture
    private lateinit var camera: Camera
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, Constants.REQUIRED_PERMISSIONS,
                Constants.REQUEST_CODE_PERMISSIONS
            )
        }

        binding.photoClickBtn.setOnClickListener {
            takePhoto()
        }

        var clicked = 0
        binding.videoRecordBtn.setOnClickListener {
            clicked += 1
            if (clicked > 2) clicked = 1
            when (clicked) {
                1 -> {
                    it.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
                    startRecording()
                }
                2 -> {
                    it.setBackgroundColor(ContextCompat.getColor(this, R.color.purple))
                    videoCapture.stopRecording()
                }
            }
        }

        binding.flipBtn.setOnClickListener {
            lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            startCamera()
        }

        binding.flashOff.setOnClickListener {
            setFlashIcon()
        }
    }

    private fun setFlashIcon() {
        if (::camera.isInitialized) {
            if (camera.cameraInfo.hasFlashUnit()) {
                if (camera.cameraInfo.torchState.value == TorchState.ON) {
                    camera.cameraControl.enableTorch(false)
                    binding.flasOn.visibility = View.INVISIBLE
                    binding.flashOff.visibility = View.VISIBLE
                } else {
                    camera.cameraControl.enableTorch(true)
                    binding.flasOn.visibility = View.VISIBLE
                    binding.flashOff.visibility = View.INVISIBLE
                }
            } else {
                Toast.makeText(this, "Flash is not available", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(Constants.TAG, "Camera not initialized")
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let { mFile ->
            File(mFile, resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }
        return mediaDir ?: filesDir
    }

    private fun takePhoto() {
        imageCapture = imageCapture ?: return
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(Constants.FILE_NAME_FORMAT, Locale.getDefault()).format(System.currentTimeMillis()) + ".jpg"
        )
        val outputOption = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOption, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val msg = "Photo saved:"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(Constants.TAG, "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    @SuppressLint("RestrictedApi")
    private fun startRecording() {
        videoCapture = videoCapture ?: return
        val videoFile = File(
            outputDirectory,
            SimpleDateFormat(Constants.FILE_NAME_FORMAT, Locale.getDefault()).format(System.currentTimeMillis()) + ".mp4"
        )
        val outputOption = VideoCapture.OutputFileOptions.Builder(videoFile).build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        videoCapture.startRecording(
            outputOption, ContextCompat.getMainExecutor(this),
            object : VideoCapture.OnVideoSavedCallback {
                override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(videoFile)
                    val msg = "Video saved: $savedUri"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }

                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    Log.e(Constants.TAG, "Video capture failed: $message", cause)
                }
            }
        )

    }

    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also { mPreview ->
                mPreview.setSurfaceProvider(binding.CameraView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()
            videoCapture = VideoCapture.Builder().build()

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture
                )
            } catch (e: Exception) {
                Log.e(Constants.TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() =
        Constants.REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                baseContext, it
            ) == PackageManager.PERMISSION_GRANTED
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
