package com.tinkismee.floodguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class map : Fragment() {

    private lateinit var mapView: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private lateinit var createSOS: Button
    private lateinit var dangerZones: Button

    private val LOCATION_REQUEST_CODE = 1001

    // ====== overlays lấy từ API reports ======
    private val reportMarkers = mutableListOf<Marker>()
    private val reportCircles = mutableListOf<Polygon>()

    // ====== overlays mẫu ======
    private val sampleMarkers = mutableListOf<Marker>()
    private val sampleCircles = mutableListOf<Polygon>()

    // ====== polling ======
    private val handler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null
    private var isPolling = false
    private val POLL_INTERVAL_MS = 5000L // 5 giây

    // ====== toggle danger circles ======
    private var showDangerCircles = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = requireContext().packageName
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.map)
        createSOS = view.findViewById(R.id.createSOS)
        dangerZones = view.findViewById(R.id.dangerZonesBtn)

        initMap()
        requestLocationPermissionIfNeeded()

        addSafePointMarker()
        addSampleDangerPoints()

        createSOS.setOnClickListener {
            startActivity(Intent(requireContext(), CreateSOS::class.java))
        }

        dangerZones.setOnClickListener {
            showDangerCircles = !showDangerCircles
            updateDangerCirclesVisibility()
        }

        // set text ban đầu
        updateDangerCirclesVisibility()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        myLocationOverlay?.enableMyLocation()

        startPollingReports()
    }

    override fun onPause() {
        super.onPause()

        stopPollingReports()

        myLocationOverlay?.disableMyLocation()
        mapView.onPause()
    }

    // ======================== INIT MAP ========================

    private fun initMap() {
        mapView.setMultiTouchControls(true)

        val defaultPoint = GeoPoint(10.8231, 106.6297) // HCM
        mapView.controller.setZoom(13.0)
        mapView.controller.setCenter(defaultPoint)
    }

    // ======================== LOCATION ========================

    private fun requestLocationPermissionIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            enableMyLocation()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        }
    }

    private fun enableMyLocation() {
        if (myLocationOverlay != null) return

        myLocationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(requireContext()),
            mapView
        ).apply {
            enableMyLocation()
            enableFollowLocation()
        }

        mapView.overlays.add(myLocationOverlay)

        myLocationOverlay?.runOnFirstFix {
            activity?.runOnUiThread {
                val loc = myLocationOverlay?.myLocation ?: return@runOnUiThread
                val myPoint = GeoPoint(loc.latitude, loc.longitude)
                mapView.controller.setZoom(16.0)
                mapView.controller.animateTo(myPoint)
            }
        }

        mapView.invalidate()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            enableMyLocation()
        }
    }

    // ======================== POLLING ========================

    private fun startPollingReports() {
        if (isPolling) return
        isPolling = true

        pollingRunnable = object : Runnable {
            override fun run() {
                loadReportsAndDraw()
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }

        handler.post(pollingRunnable!!)
    }

    private fun stopPollingReports() {
        isPolling = false
        pollingRunnable?.let { handler.removeCallbacks(it) }
        pollingRunnable = null
    }

    // ======================== API REPORTS -> DRAW ========================

    private fun loadReportsAndDraw() {
        RetrofitClient.instance.getReports().enqueue(object : Callback<List<Report>> {

            override fun onResponse(call: Call<List<Report>>, response: Response<List<Report>>) {
                if (!response.isSuccessful) {
                    Log.e("DEBUG", "Get reports failed: ${response.code()} ${response.errorBody()?.string()}")
                    return
                }

                val reports = (response.body() ?: emptyList())
                    .filter { it.status == "pending" }

                clearReportOverlays()

                reports.forEach { r ->
                    drawReport(r)
                }

                // ✅ nếu đang tắt vòng tròn thì đảm bảo nó không tự bật lại khi polling
                updateDangerCirclesVisibility()

                mapView.invalidate()
            }

            override fun onFailure(call: Call<List<Report>>, t: Throwable) {
                Log.e("DEBUG", "API error: ${t.message}")
            }
        })
    }

    private fun drawReport(r: Report) {
        val center = GeoPoint(r.lat, r.lng)

        val radiusMeters = when (r.type) {
            "sos" -> 350.0
            "flood_report" -> 800.0
            "resource_request" -> 500.0
            else -> 400.0
        }

        val circle = createDangerCircle(center, radiusMeters)

        val marker = Marker(mapView).apply {
            position = center
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            title = when (r.type) {
                "sos" -> "🆘 SOS"
                "flood_report" -> "⚠️ Báo cáo lụt"
                "resource_request" -> "📦 Yêu cầu tiếp tế"
                else -> "Report"
            }

            // message nằm trên
            snippet = r.message ?: ""

            icon = scale(R.drawable.location_exclamation, 0.08f)
        }

        reportCircles.add(circle)
        reportMarkers.add(marker)

        // ✅ chỉ add circle nếu đang bật
        if (showDangerCircles) {
            mapView.overlays.add(circle)
        }
        mapView.overlays.add(marker)
    }

    private fun clearReportOverlays() {
        reportMarkers.forEach { mapView.overlays.remove(it) }
        reportCircles.forEach { mapView.overlays.remove(it) }
        reportMarkers.clear()
        reportCircles.clear()
    }

    // ======================== SAFE POINT ========================

    private fun addSafePointMarker() {
        val safePoint = GeoPoint(10.7769942, 106.6927272)

        val safeMarker = Marker(mapView).apply {
            position = safePoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            title = "✅ Điểm tập kết an toàn"
            snippet = "Tập kết tại đây để đảm bảo an toàn"

            icon = scale(R.drawable.location_check, 0.08f)
        }

        mapView.overlays.add(safeMarker)
        mapView.invalidate()
    }

    // ======================== SAMPLE DANGER ========================

    private fun addSampleDangerPoints() {
        val samples = listOf(
            Triple(GeoPoint(10.7842137, 106.6738485), 800.0, "⚠️ Cảnh báo điểm ngập lụt"),
            Triple(GeoPoint(10.7690412, 106.7406133), 1500.0, "⚠️ Cảnh báo điểm ngập lụt"),
            Triple(GeoPoint(10.75518, 106.7649042), 2650.0, "⚠️ Cảnh báo điểm ngập lụt"),
            Triple(GeoPoint(10.789693, 106.7866485), 400.0, "⚠️ Cảnh báo điểm ngập lụt")
        )

        samples.forEach { (center, radius, title) ->
            val circle = createDangerCircle(center, radius)

            val marker = Marker(mapView).apply {
                position = center
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                this.title = title
                snippet = "Lũ có thể dâng cao trong vòng ${(15..50).random()} phút nữa"
                icon = scale(R.drawable.location_exclamation, 0.08f)
            }

            sampleCircles.add(circle)
            sampleMarkers.add(marker)

            // ✅ chỉ add circle nếu đang bật
            if (showDangerCircles) {
                mapView.overlays.add(circle)
            }
            mapView.overlays.add(marker)
        }

        mapView.invalidate()
    }

    // ======================== TOGGLE DANGER CIRCLES ========================

    private fun updateDangerCirclesVisibility() {
        val allCircles = reportCircles + sampleCircles

        if (showDangerCircles) {
            allCircles.forEach { circle ->
                if (!mapView.overlays.contains(circle)) {
                    mapView.overlays.add(circle)
                }
            }
            dangerZones.text = "Tắt vùng nguy hiểm"
        } else {
            allCircles.forEach { circle ->
                mapView.overlays.remove(circle)
            }
            dangerZones.text = "Bật vùng nguy hiểm"
        }

        mapView.invalidate()
    }

    // ======================== DANGER CIRCLE FACTORY ========================

    private fun createDangerCircle(center: GeoPoint, radiusMeters: Double): Polygon {
        return object : Polygon(mapView) {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: MapView?): Boolean = false
            override fun onLongPress(e: android.view.MotionEvent?, mapView: MapView?): Boolean = false
        }.apply {
            points = Polygon.pointsAsCircle(center, radiusMeters)
            fillColor = Color.argb(70, 255, 0, 0)

            outlinePaint.apply {
                color = Color.RED
                strokeWidth = 6f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(18f, 14f), 0f)
                isAntiAlias = true
            }
        }
    }

    // ======================== ICON SCALE ========================

    private fun scale(resId: Int, scale: Float): BitmapDrawable {
        val drawable = ContextCompat.getDrawable(requireContext(), resId)!!
        val original = drawable.toBitmap()
        val newW = (original.width * scale).toInt()
        val newH = (original.height * scale).toInt()
        val bitmap = Bitmap.createScaledBitmap(original, newW, newH, true)
        return bitmap.toDrawable(resources)
    }
}
