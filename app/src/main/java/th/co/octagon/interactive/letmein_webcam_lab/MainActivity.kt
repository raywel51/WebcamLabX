package th.co.octagon.interactive.letmein_webcam_lab

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import th.co.octagon.interactive.letmein_webcam_lab.camerax.CameraXViewModel
import th.co.octagon.interactive.letmein_webcam_lab.databinding.ActivityMainBinding
import java.util.Timer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var usbManager: UsbManager
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private val usbPermissionAction = "com.example.USB_PERMISSION"
    private val vendorId = 7119 // Replace with your actual vendor ID

    private var cameraSelectorBack: CameraSelector? = null
    private var cameraSelectorFront: CameraSelector? = null
    private var cameraAvailable: Int? = null
    private var previewUseCase: Preview? = null

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

        binding = ActivityMainBinding.inflate(layoutInflater)
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
            println(cameraAvailable)
        } 

        binding.btnCapture.setOnClickListener {

        }
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

    private fun setupCamera() {
        cameraSelectorBack = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        cameraSelectorFront = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val provider: ProcessCameraProvider = cameraProviderFuture.get()

            try {
                // Check if camera permission is granted
                if (isCameraPermissionGranted()) {
                    cameraProvider = provider
                    bindCameraUseCases()
                } else {
                    // Request camera permission if not granted
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CAMERA),
                        PERMISSION_CAMERA_REQUEST
                    )
                }
            } catch (e: Exception) {
                // Handle exceptions
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
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

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_CAMERA_REQUEST = 1
    }
}