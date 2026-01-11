package com.tinkismee.floodguard

import android.os.Bundle
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

class ManageSOSFragment : Fragment() {

    private lateinit var reportList: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_manage_sos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        reportList = view.findViewById(R.id.reportList)

        loadReports()
    }

    private fun loadReports() {
        RetrofitClient.instance.getReports().enqueue(object : Callback<List<Report>> {
            override fun onResponse(call: Call<List<Report>>, response: Response<List<Report>>) {
                if (!isAdded || view == null) return

                if (!response.isSuccessful) {
                    Log.e("DEBUG", "Get reports failed: ${response.code()} ${response.errorBody()?.string()}")
                    return
                }

                val reports = response.body() ?: emptyList()

                // ✅ Lọc tin SOS / flood_report / resource_request
                val filtered = reports.filter {
                    it.type == "sos" || it.type == "flood_report" || it.type == "resource_request"
                }.sortedByDescending { it.id }

                renderReports(filtered)
            }

            override fun onFailure(call: Call<List<Report>>, t: Throwable) {
                if (!isAdded || view == null) return
                Log.e("DEBUG", "API error: ${t.message}")
            }
        })
    }

    private fun renderReports(reports: List<Report>) {
        reportList.removeAllViews()
        val inflater = layoutInflater

        reports.forEach { r ->
            val item = inflater.inflate(R.layout.item_report, reportList, false)

            val tvType = item.findViewById<TextView>(R.id.tvType)
            val tvMessage = item.findViewById<TextView>(R.id.tvMessage)
            val tvLocation = item.findViewById<TextView>(R.id.tvLocation)
            val tvStatus = item.findViewById<TextView>(R.id.tvStatus)
            val tvTime = item.findViewById<TextView>(R.id.tvTime)

            tvType.text = when (r.type) {
                "sos" -> "🆘 SOS"
                "flood_report" -> "⚠️ Báo cáo lụt"
                "resource_request" -> "📦 Yêu cầu tiếp tế"
                else -> r.type
            }

            tvMessage.text = "Tin nhắn: ${r.message ?: ""}"
            tvLocation.text = "Vị trí: ${r.lat}, ${r.lng}"
            tvStatus.text = "Trạng thái: ${r.status ?: "pending"}"
            tvTime.text = "Thời gian: ${r.created_at ?: ""}"

            reportList.addView(item)
        }
    }
}
