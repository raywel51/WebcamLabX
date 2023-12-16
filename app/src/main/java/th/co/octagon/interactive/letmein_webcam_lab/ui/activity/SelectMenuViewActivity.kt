package th.co.octagon.interactive.letmein_webcam_lab.ui.activity

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import th.co.octagon.interactive.letmein_webcam_lab.R
import th.co.octagon.interactive.letmein_webcam_lab.databinding.ActivitySelectMenuViewBinding


class SelectMenuViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySelectMenuViewBinding
    private val mContext = this@SelectMenuViewActivity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySelectMenuViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCameraX.setOnClickListener {
            cameraXView()
        }

        binding.btnCamera2.setOnClickListener {
            camera2View()
        }
    }

    private fun cameraXView() {
        changeActivityView(CameraXViewActivity::class.java)
    }

    private fun camera2View() {
        changeActivityView(Camera2ViewActivity::class.java)
    }

    private fun changeActivityView(java: Class<out Activity>) {
        val intentLevel = Intent(this, java)
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val options = ActivityOptions.makeCustomAnimation(baseContext,
                R.anim.fade_in,
                R.anim.fade_out
            )
            startActivity(intentLevel, options.toBundle())
        } else {
            startActivity(intentLevel)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }
}