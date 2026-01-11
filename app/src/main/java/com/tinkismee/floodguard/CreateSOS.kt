package com.tinkismee.floodguard

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class CreateSOS : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var spinner: Spinner
    private lateinit var getLocation: Button
    private lateinit var viewLocation: TextView
    private lateinit var createSOS: Button
    private lateinit var backBtn: Button


    private val locationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val LOCATION_PERMISSION_REQUEST = 111

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_sos)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initVars()
        initSpinner()

        getLocation.setOnClickListener {
            checkPermissionAndGetLocation()
        }
        backBtn.setOnClickListener {
            finish()
        }
    }

    private fun checkPermissionAndGetLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    private fun getCurrentLocation() {
        locationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude

                    viewLocation.text = "Lat: $lat\nLng: $lng\nĐang lấy địa chỉ..."

                    lifecycleScope.launch {
                        val addressText = getAddressFromLatLng(lat, lng)
                        viewLocation.text = "Lat: $lat\nLng: $lng\nĐịa chỉ: $addressText"
                    }
                } else {
                    viewLocation.text = "Không lấy được location (hãy bật GPS và thử lại)"
                }
            }
            .addOnFailureListener {
                viewLocation.text = "Lỗi lấy location: ${it.message}"
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                viewLocation.text = "Bạn chưa cấp quyền Location"
            }
        }
    }

    private fun initVars() {
        nameInput = findViewById(R.id.nameInput)
        spinner = findViewById(R.id.spinner)
        getLocation = findViewById(R.id.getLocation)
        viewLocation = findViewById(R.id.viewLocation)
        createSOS = findViewById(R.id.createSOS)
        backBtn = findViewById(R.id.backBtn)
    }

    private fun initSpinner() {
        val items = listOf("Báo cáo lụt", "Yêu cầu giải cứu", "Yêu cầu tiếp tế")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private suspend fun getAddressFromLatLng(lat: Double, lng: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@CreateSOS, Locale("vi", "VN"))
                val addresses = geocoder.getFromLocation(lat, lng, 1)

                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    address.getAddressLine(0) ?: "Không tìm thấy địa chỉ"
                } else {
                    "Không tìm thấy địa chỉ"
                }
            } catch (e: Exception) {
                "Lỗi chuyển đổi: ${e.message}"
            }
        }
    }
}
