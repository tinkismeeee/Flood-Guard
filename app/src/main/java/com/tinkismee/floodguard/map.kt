package com.tinkismee.floodguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.widget.Button
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class map : Fragment() {

    private lateinit var mapView: MapView

    private var myLocationOverlay: MyLocationNewOverlay? = null

    private val dangerMarkers = mutableListOf<Marker>()
    private val dangerCircles = mutableListOf<Polygon>()
    private lateinit var createSOS: Button

    private val LOCATION_REQUEST_CODE = 1001

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

        initMap()
        requestLocationPermissionIfNeeded()
        createSOS = view.findViewById(R.id.createSOS)
        createSOS.setOnClickListener {
            startActivity(Intent(requireContext(), CreateSOS::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        myLocationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        myLocationOverlay?.disableMyLocation()
        mapView.onPause()
    }

    // ======================== INIT MAP ========================

    private fun initMap() {
        mapView.setMultiTouchControls(true)

        val defaultPoint = GeoPoint(10.8231, 106.6297)
        mapView.controller.setZoom(13.0)
        mapView.controller.setCenter(defaultPoint)

        addDangerZone(
            center = GeoPoint(10.83, 106.62),
            radiusMeters = 800.0,
            title = "Cảnh báo: Lũ quét",subDescription = "Test",
            snippet = "Test"
        )

        addDangerZone(
            center = GeoPoint(10.81, 106.65),
            radiusMeters = 500.0,
            title = "Cảnh báo: Ngập",
            subDescription = "Test",
            snippet = "Test"
        )
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

        // bay tới vị trí hiện tại khi GPS fix lần đầu
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

    // ======================== DANGER ZONE ========================

    /**
     * ✅ Hàm này sẽ:
     * - Vẽ marker icon location_exclamation
     * - Vẽ vòng tròn đỏ bán kính nguy hiểm
     */
    private fun addDangerZone(center: GeoPoint, radiusMeters: Double, title: String, subDescription: String, snippet: String) {
        val circle = object : Polygon(mapView) {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: MapView?): Boolean {
                return false
            }
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

        val dangerMarker = Marker(mapView).apply {
            position = center
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = scale(R.drawable.location_exclamation, 0.08f)
            this.title = title
            this.subDescription = subDescription
            this.snippet = snippet
        }

        dangerMarkers.add(dangerMarker)
        mapView.overlays.add(dangerMarker)


        dangerCircles.add(circle)
        mapView.overlays.add(circle)

        mapView.invalidate()
    }

    /**
     * ✅ Dùng khi bạn load realtime từ API:
     * clear old zones rồi add lại theo dữ liệu mới.
     */
    private fun clearDangerZones() {
        dangerMarkers.forEach { mapView.overlays.remove(it) }
        dangerCircles.forEach { mapView.overlays.remove(it) }
        dangerMarkers.clear()
        dangerCircles.clear()
        mapView.invalidate()
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
