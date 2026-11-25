package com.example.ridscanner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ridscanner.data.OdidParser
import com.example.ridscanner.data.UasRecord
import com.example.ridscanner.databinding.ItemDeviceBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceAdapter(
    private val onClick: (UasRecord) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    private val items = mutableListOf<UasRecord>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun submit(list: List<UasRecord>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        val b = holder.binding
        val idLabel = r.uasId.ifBlank { "未收到身份报文" }
        b.tvId.text = "[${OdidParser.uaTypeName(r.uaType)}] $idLabel"

        val sub = buildString {
            append("RSSI ${r.rssi} dBm")
            if (r.hasLocation) {
                append(" · ${OdidParser.statusName(r.status)}")
                if (!r.speedHorizontal.isNaN()) append(" · ${String.format("%.1f", r.speedHorizontal)} m/s")
                if (!r.altGeo.isNaN()) append(" · 高度 ${String.format("%.0f", r.altGeo)} m")
            }
            append(" · 更新于 ${timeFmt.format(Date(r.lastSeen))}")
        }
        b.tvSub.text = sub

        b.tvPos.text = if (r.hasPosition()) {
            "飞机 ${String.format("%.6f, %.6f", r.latitude, r.longitude)}" +
                (if (r.hasOperator) "\n飞手 ${String.format("%.6f, %.6f", r.operatorLat, r.operatorLon)}" else "")
        } else {
            "（尚未收到位置报文，可先在列表查看信号）"
        }

        b.root.setOnClickListener { onClick(r) }
    }

    override fun getItemCount() = items.size
}
