package com.magic.pagetime

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var textPhotoStatus: TextView

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) saveBaseImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("pagetime_prefs", MODE_PRIVATE)

        val editApiLink = findViewById<EditText>(R.id.editApiLink)
        val editApiKey = findViewById<EditText>(R.id.editApiKey)
        val radioGroup = findViewById<RadioGroup>(R.id.radioTimeGroup)
        val editCustomDateTime = findViewById<EditText>(R.id.editCustomDateTime)
        val switchAutoLock = findViewById<Switch>(R.id.switchAutoLock)
        val switchVibrate = findViewById<Switch>(R.id.switchVibrate)
        val editMarginLeft = findViewById<EditText>(R.id.editMarginLeft)
        val editMarginRight = findViewById<EditText>(R.id.editMarginRight)
        val editMarginTop = findViewById<EditText>(R.id.editMarginTop)
        val editMarginBottom = findViewById<EditText>(R.id.editMarginBottom)
        val editTextRotation = findViewById<EditText>(R.id.editTextRotation)
        val editTextColor = findViewById<EditText>(R.id.editTextColor)
        val editTextOpacity = findViewById<EditText>(R.id.editTextOpacity)
        val editTextSizePx = findViewById<EditText>(R.id.editTextSizePx)
        val editPageNumber = findViewById<EditText>(R.id.editPageNumber)
        val editNotificationPackage = findViewById<EditText>(R.id.editNotificationPackage)
        val btnUploadPhoto = findViewById<Button>(R.id.btnUploadPhoto)
        textPhotoStatus = findViewById(R.id.textPhotoStatus)
        val btnConfirm = findViewById<Button>(R.id.btnConfirm)
        val btnDeviceAdmin = findViewById<Button>(R.id.btnDeviceAdminSettings)
        val btnNotificationAccess = findViewById<Button>(R.id.btnNotificationAccess)

        editApiLink.setText(prefs.getString("api_link", ""))
        editApiKey.setText(prefs.getString("api_key", "value"))
        switchAutoLock.isChecked = prefs.getBoolean("auto_lock", true)
        switchVibrate.isChecked = prefs.getBoolean("vibrate_on_complete", true)
        editMarginLeft.setText(prefs.getInt("margin_left", 33).toString())
        editMarginRight.setText(prefs.getInt("margin_right", 22).toString())
        editMarginTop.setText(prefs.getInt("margin_top", 33).toString())
        editMarginBottom.setText(prefs.getInt("margin_bottom", 33).toString())
        editTextRotation.setText(prefs.getInt("text_rotation", 0).toString())
        editTextColor.setText(prefs.getString("text_color", "#1C1C1E"))
        editTextOpacity.setText(prefs.getInt("text_opacity", 100).toString())
        editTextSizePx.setText(prefs.getInt("text_size_px", 40).toString())
        editPageNumber.setText(prefs.getString("page_number", "246"))
        editNotificationPackage.setText(prefs.getString("notification_package", "com.android.chrome"))

        when (prefs.getString("time_setting", "3h")) {
            "3h" -> radioGroup.check(R.id.radio3h)
            "10h" -> radioGroup.check(R.id.radio10h)
            "24h" -> radioGroup.check(R.id.radio24h)
            "3d" -> radioGroup.check(R.id.radio3d)
            "custom" -> radioGroup.check(R.id.radioCustom)
        }
        editCustomDateTime.setText(prefs.getString("custom_datetime", ""))
        editCustomDateTime.visibility =
            if (radioGroup.checkedRadioButtonId == R.id.radioCustom) View.VISIBLE else View.GONE

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            editCustomDateTime.visibility =
                if (checkedId == R.id.radioCustom) View.VISIBLE else View.GONE
        }

        updatePhotoStatus()

        btnUploadPhoto.setOnClickListener { pickImage.launch("image/*") }

        btnDeviceAdmin.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(this, MyDeviceAdminReceiver::class.java)
            )
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Needed to auto-lock the phone after the effect."
            )
            startActivity(intent)
        }

        btnNotificationAccess.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnConfirm.setOnClickListener {
            if (!getBaseImageFile().exists()) {
                Toast.makeText(this, "Upload a base photo first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val colorInput = editTextColor.text.toString().trim()
            val validColor = try {
                android.graphics.Color.parseColor(if (colorInput.startsWith("#")) colorInput else "#$colorInput")
                if (colorInput.startsWith("#")) colorInput else "#$colorInput"
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid color, using default", Toast.LENGTH_SHORT).show()
                "#1C1C1E"
            }

            val opacity = (editTextOpacity.text.toString().toIntOrNull() ?: 100).coerceIn(0, 100)
            val rotation = (editTextRotation.text.toString().toIntOrNull() ?: 0).coerceIn(-45, 45)
            val sizePx = (editTextSizePx.text.toString().toIntOrNull() ?: 40).coerceIn(8, 400)

            val timeKey = when (radioGroup.checkedRadioButtonId) {
                R.id.radio3h -> "3h"
                R.id.radio10h -> "10h"
                R.id.radio24h -> "24h"
                R.id.radio3d -> "3d"
                R.id.radioCustom -> "custom"
                else -> "3h"
            }
            prefs.edit()
                .putString("api_link", editApiLink.text.toString().trim())
                .putString("api_key", editApiKey.text.toString().trim())
                .putString("time_setting", timeKey)
                .putString("custom_datetime", editCustomDateTime.text.toString().trim())
                .putBoolean("auto_lock", switchAutoLock.isChecked)
                .putBoolean("vibrate_on_complete", switchVibrate.isChecked)
                .putInt("margin_left", editMarginLeft.text.toString().toIntOrNull() ?: 33)
                .putInt("margin_right", editMarginRight.text.toString().toIntOrNull() ?: 22)
                .putInt("margin_top", editMarginTop.text.toString().toIntOrNull() ?: 33)
                .putInt("margin_bottom", editMarginBottom.text.toString().toIntOrNull() ?: 33)
                .putInt("text_rotation", rotation)
                .putString("text_color", validColor)
                .putInt("text_opacity", opacity)
                .putInt("text_size_px", sizePx)
                .putString("page_number", editPageNumber.text.toString().trim())
                .putString("notification_package", editNotificationPackage.text.toString().trim())
                .apply()

            val serviceIntent = Intent(this, PollingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            startActivity(Intent(this, BlackScreenActivity::class.java))
            finish()
        }
    }

    private fun getBaseImageFile(): File = File(filesDir, "base_image.jpg")

    private fun saveBaseImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(getBaseImageFile()).use { output -> input.copyTo(output) }
            }
            updatePhotoStatus()
            Toast.makeText(this, "Photo saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save photo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updatePhotoStatus() {
        textPhotoStatus.text = if (getBaseImageFile().exists()) "Photo selected ✓" else "No photo selected"
    }
}
