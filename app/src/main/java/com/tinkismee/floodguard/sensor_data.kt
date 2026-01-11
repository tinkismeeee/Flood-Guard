package com.tinkismee.floodguard

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class sensor_data : Fragment() {

    private lateinit var sensorList: LinearLayout
    private lateinit var lastUpdated: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null
    private var isPolling = false
    private val POLL_INTERVAL_MS = 5000L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sensor_data, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sensorList = view.findViewById(R.id.sensorList)
        lastUpdated = view.findViewById(R.id.lastUpdated)

        loadSensors()
    }

    override fun onResume() {
        super.onResume()
        startPollingSensors()
    }

    override fun onPause() {
        super.onPause()
        stopPollingSensors()
    }

    private fun startPollingSensors() {
        if (isPolling) return
        isPolling = true

        pollingRunnable = object : Runnable {
            override fun run() {
                loadSensors()
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }

        handler.post(pollingRunnable!!)
    }

    private fun stopPollingSensors() {
        isPolling = false
        pollingRunnable?.let { handler.removeCallbacks(it) }
        pollingRunnable = null
    }

    private fun loadSensors() {
        RetrofitClient.instance.getSensors().enqueue(object : Callback<List<Sensor>> {

            override fun onResponse(call: Call<List<Sensor>>, response: Response<List<Sensor>>) {
                if (!isAdded || view == null) return

                if (!response.isSuccessful) {
                    lastUpdated.text = "Lỗi load sensors: ${response.code()}"
                    return
                }

                val sensors = response.body() ?: emptyList()
                renderSensors(sensors)
                lastUpdated.text = "Cập nhật OK"
            }

            override fun onFailure(call: Call<List<Sensor>>, t: Throwable) {
                // ✅ Fragment không còn attach -> bỏ qua
                if (!isAdded || view == null) return

                lastUpdated.text = "Không kết nối được server"
            }
        })
    }


    private fun renderSensors(sensors: List<Sensor>) {
        if (!isAdded || view == null) return
        sensorList.removeAllViews()

        val inflater = layoutInflater

        sensors.forEach { sensor ->
            val item = inflater.inflate(R.layout.item_sensor, sensorList, false)

            val tvSensorId = item.findViewById<TextView>(R.id.tvSensorId)
            val tvWarning = item.findViewById<TextView>(R.id.tvWarning)
            val tvWaterLevel = item.findViewById<TextView>(R.id.tvWaterLevel)
            val tvFlowRate = item.findViewById<TextView>(R.id.tvFlowRate)
            val tvLatLng = item.findViewById<TextView>(R.id.tvLatLng)
            val tvRecordedAt = item.findViewById<TextView>(R.id.tvRecordedAt)

            tvSensorId.text = sensor.sensor_id

            val warningText = when (sensor.warning_level) {
                "normal" -> "🟢 NORMAL"
                "warning" -> "🟡 WARNING"
                "critical" -> "🔴 CRITICAL"
                else -> sensor.warning_level
            }
            tvWarning.text = "Mức cảnh báo: $warningText"

            tvWaterLevel.text = "Mực nước: ${sensor.water_level ?: 0.0} cm"
            tvFlowRate.text = "Lưu lượng: ${sensor.flow_rate ?: 0.0}"
            tvLatLng.text = "Tọa độ: ${sensor.lat}, ${sensor.lng}"
            tvRecordedAt.text = "Ghi nhận: ${sensor.recorded_at}"

            sensorList.addView(item)
        }
    }
}
