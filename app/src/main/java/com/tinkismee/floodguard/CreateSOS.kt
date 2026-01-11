package com.tinkismee.floodguard

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class CreateSOS : AppCompatActivity() {

    private lateinit var messageInput: EditText
    private lateinit var spinner: Spinner
    private lateinit var getLocation: Button
    private lateinit var latTv: TextView
    private lateinit var lngTv: TextView
    private lateinit var realLocationTv: TextView
    private lateinit var createSOSBtn: Button
    private lateinit var backBtn: Button

    private var latL: Double? = null
    private var lngL: Double? = null

    private val locationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val LOCATION_PERMISSION_REQUEST = 111

    // Map label VN -> enum DB
    private val typeMap = mapOf(
        "Báo cáo lụt" to "flood_report",
        "Yêu cầu giải cứu" to "sos",
        "Yêu cầu tiếp tế" to "resource_request"
    )

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

        backBtn.setOnClickListener { finish() }

        getLocation.setOnClickListener {
            checkPermissionAndGetLocation()
        }

        createSOSBtn.setOnClickListener {
            createSOS()
        }
    }

    private fun initVars() {
        // NOTE: bạn đang dùng id nameInput trong XML -> mình vẫn lấy nó làm messageInput
        messageInput = findViewById(R.id.messageInput)

        spinner = findViewById(R.id.spinner)
        getLocation = findViewById(R.id.getLocation)

        latTv = findViewById(R.id.lat)
        lngTv = findViewById(R.id.lng)
        realLocationTv = findViewById(R.id.realLocation)

        createSOSBtn = findViewById(R.id.createSOS)
        backBtn = findViewById(R.id.backBtn)
    }

    private fun initSpinner() {
        val items = typeMap.keys.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
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
        val tokenSource = CancellationTokenSource()

        locationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            tokenSource.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                latL = location.latitude
                lngL = location.longitude

                latTv.text = "Lat: ${latL}"
                lngTv.text = "Lng: ${lngL}"
                realLocationTv.text = "Đang lấy địa chỉ..."

                lifecycleScope.launch {
                    val addressText = getAddressFromLatLng(latL!!, lngL!!)
                    realLocationTv.text = "Địa chỉ hiện tại: $addressText"
                }
            } else {
                realLocationTv.text = "Không lấy được vị trí (GPS chưa sẵn sàng)"
            }
        }.addOnFailureListener {
            realLocationTv.text = "Lỗi lấy vị trí: ${it.message}"
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
                Toast.makeText(this, "Bạn chưa cấp quyền Location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun getAddressFromLatLng(lat: Double, lng: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@CreateSOS, Locale("vi", "VN"))
                val addresses = geocoder.getFromLocation(lat, lng, 1)

                if (!addresses.isNullOrEmpty()) {
                    addresses[0].getAddressLine(0) ?: "Không tìm thấy địa chỉ"
                } else {
                    "Không tìm thấy địa chỉ"
                }
            } catch (e: Exception) {
                "Lỗi chuyển đổi: ${e.message}"
            }
        }
    }

    private fun createSOS() {
        val message = messageInput.text.toString().trim()

        val selectedLabel = spinner.selectedItem.toString()
        val typeEnum = typeMap[selectedLabel] ?: "sos"

        val latValue = latL
        val lngValue = lngL

        val userId = 1

        if (message.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập nội dung", Toast.LENGTH_SHORT).show()
            return
        }

        if (latValue == null || lngValue == null) {
            Toast.makeText(this, "Bạn chưa lấy vị trí hiện tại", Toast.LENGTH_SHORT).show()
            return
        }

        val sos = SOS(
            user_id = userId,
            type = typeEnum,
            message = message,
            lat = latValue,
            lng = lngValue
        )

        RetrofitClient.instance.createSOS(sos).enqueue(object : Callback<SOS> {
            override fun onResponse(call: Call<SOS>, response: Response<SOS>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@CreateSOS, "Tạo yêu cầu thành công!", Toast.LENGTH_SHORT).show()
                    Log.i("DEBUG", "Created: ${response.body()}")
                    finish()
                } else {
                    val err = response.errorBody()?.string()
                    Log.e("DEBUG", "Create failed ${response.code()}: $err")
                    Toast.makeText(this@CreateSOS, "Lỗi: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<SOS>, t: Throwable) {
                Log.e("DEBUG", "API error: ${t.message}")
                Toast.makeText(this@CreateSOS, "Không kết nối server", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
