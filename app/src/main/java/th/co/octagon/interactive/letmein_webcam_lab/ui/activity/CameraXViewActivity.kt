package th.co.octagon.interactive.letmein_webcam_lab.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.Image
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import th.co.octagon.interactive.letmein_webcam_lab.databinding.ActivityCameraXBinding
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraXViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraXBinding
    private var mContext = this@CameraXViewActivity

    private lateinit var usbManager: UsbManager
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private val usbPermissionAction = "com.example.USB_PERMISSION"
    private val vendorId = 7119 // Replace with your actual vendor ID

    private var cameraSelectorBack: CameraSelector? = null
    private var cameraSelectorFront: CameraSelector? = null
    private var cameraAvailable: Int? = null
    private var previewUseCase: Preview? = null

    private lateinit var cameraManager: CameraManager

    private var imageCapture: ImageCapture? = null

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (usbPermissionAction == action) {
                synchronized(this) {
                    val usbDevice: UsbDevice? =
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as UsbDevice?

                    usbDevice?.let {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            setupCamera()
                        } else {
                        }
                    }
                }
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                checkAndRequestUSBPermission()
            } else {
                // Permission denied, handle accordingly
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCameraXBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupCamera()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            checkAndRequestUSBPermission()
        }

        binding.btnChange.setOnClickListener {
            takePhoto()
        } 

        binding.btnCapture.setOnClickListener {
            getCameraDeviceList()
        }
    }

    fun Image.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(usbPermissionAction)
        registerReceiver(usbPermissionReceiver, filter)
        checkAndRequestUSBPermission()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(usbPermissionReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()

        stopCamera()
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll() // Release all the resources associated with the camera
        cameraProvider = null // Set the cameraProvider to null to release the reference
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun checkAndRequestUSBPermission() {
        val deviceList: HashMap<String, UsbDevice>? = usbManager.deviceList

        deviceList?.values?.forEach { device ->
            println(device.vendorId)
            println(device.productName)
            if (device.vendorId == vendorId) {
                val permissionIntent =
                    PendingIntent.getBroadcast(this, 0, Intent(usbPermissionAction), 0)
                usbManager.requestPermission(device, permissionIntent)
            }
        }
    }

    @OptIn(ExperimentalLensFacing::class) private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(mContext)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            if (cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) == true) {
                println("System Have DEFAULT_BACK_CAMERA")
            }

            if (cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true) {
                println("System Have DEFAULT_FRONT_CAMERA")
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(if (cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) == true)
                    CameraSelector.LENS_FACING_BACK
                else
                    CameraSelector.LENS_FACING_FRONT
                )
                .build()

            println("Select Camera $cameraSelector")

            val cameraView = binding.previewView
            val preview = Preview.Builder().build()
            imageCapture = ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            preview.setSurfaceProvider(cameraView.surfaceProvider)

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                // Handle camera setup error
            }
        }, ContextCompat.getMainExecutor(mContext))
    }

    private fun getCameraDeviceList() {

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            val cameraIdList = cameraManager.cameraIdList

            for (cameraId in cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

                // Handle camera characteristics as needed
                Log.d("CameraList", "Camera ID: $cameraId, Lens Facing: $lensFacing")
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun bindCameraUseCases() {
        bindPreviewUseCase()
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(baseContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun bindPreviewUseCase() {

        if (cameraProvider == null) {
            return
        }
        if (previewUseCase != null) {
            cameraProvider!!.unbind(previewUseCase)
        }

        previewUseCase = Preview.Builder()
            .setTargetRotation(binding.previewView.display.rotation)
            .build()

        previewUseCase!!.setSurfaceProvider(binding.previewView.surfaceProvider)


        if(!tryFrontCamera()){
            tryBackCamera()
        }

    }

    private fun tryBackCamera(): Boolean {
        var result = true
        try {
            cameraProvider!!.bindToLifecycle(
                this,
                cameraSelectorBack!!,
                previewUseCase
            )
            Log.v("Camera Select", "Connect Back Camera Is Using")
            cameraAvailable = 1
        } catch (illegalStateException: IllegalStateException) {
            Log.e(TAG, illegalStateException.message ?: "IllegalStateException")
            result = false
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e(TAG, illegalArgumentException.message ?: "IllegalArgumentException")
            result = false
        }

        return result
    }

    private fun tryFrontCamera(): Boolean {
        var result = true
        try {
            cameraProvider!!.bindToLifecycle(
                this,
                cameraSelectorFront!!,
                previewUseCase
            )
            Log.v("Camera Select", "Connect Front Camera Is Using")
            cameraAvailable = 2
        } catch (illegalStateException: IllegalStateException) {
            Log.e(TAG, illegalStateException.message ?: "IllegalStateException")
            result = false
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e(TAG, illegalArgumentException.message ?: "IllegalArgumentException")
            result = false
        }

        return result
    }

    private fun takePhoto() {
        println("click take photo")

        val outputFile = File(this@CameraXViewActivity.externalMediaDirs.first(), "photo.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture?.takePicture(outputOptions, ContextCompat.getMainExecutor(this@CameraXViewActivity), onImageSavedCallback)
    }

    private val onImageSavedCallback = object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            val savedUri = outputFileResults.savedUri

            // Convert the saved URI to a Bitmap
            val bitmap: Bitmap? = savedUri?.toBitmap(mContext)
            // Use the obtained bitmap as needed
            if (bitmap != null) {
                // Do something with the bitmap
                println("Bitmap is ok")
                binding.ivShow.setImageBitmap(bitmap)
            } else {
                // Handle the case where the bitmap is null
                println("Bitmap not is ok")
            }
        }

        override fun onError(exception: ImageCaptureException) {
            // Handle image capture error
        }
    }

    // Extension function to convert URI to Bitmap
    fun Uri.toBitmap(context: Context): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(this)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }


    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_CAMERA_REQUEST = 1
    }
}