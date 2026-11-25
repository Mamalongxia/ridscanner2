package com.example.ridscanner.ui

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ridscanner.ble.SharedStore
import com.example.ridscanner.data.OdidParser
import com.example.ridscanner.data.UasRecord
import com.example.ridscanner.databinding.ActivityMapBinding
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * 地图页：标注飞机位置（蓝色）与飞手位置（绿色）。
 * 使用 osmdroid 离线/在线 OSM 瓦片，无需 Google/高德 API key。
 */
class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid 需要在 Application 上下文初始化配置
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        map = binding.map
        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(true)

        val mac = intent.getStringExtra(EXTRA_MAC) ?: return
        val record = SharedStore.get(mac)
        if (record == null) {
            binding.txtInfo.text = "未找到该设备数据"
            return
        }

        show(record)
    }

    private fun show(r: UasRecord) {
        map.overlays.clear()

        var center: GeoPoint? = null

        if (r.hasPosition()) {
            val plane = GeoPoint(r.latitude, r.longitude)
            val planeMarker = Marker(map).apply {
                position = plane
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "飞机：${r.uasId.ifBlank { "未知" }}"
                snippet = buildPlaneSnippet(r)
                icon = resources.getDrawable(android.R.drawable.ic_menu_compass, theme)
            }
            map.overlays.add(planeMarker)
            center = plane
        }

        if (r.hasOperator) {
            val op = GeoPoint(r.operatorLat, r.operatorLon)
            val opMarker = Marker(map).apply {
                position = op
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "飞手位置"
                snippet = if (!r.operatorAlt.isNaN()) "飞手高度 ${String.format("%.0f", r.operatorAlt)} m" else "飞手位置"
                val drawable = resources.getDrawable(android.R.drawable.ic_menu_mylocation, theme)
                drawable.setTint(Color.GREEN)
                icon = drawable
            }
            map.overlays.add(opMarker)
            if (center == null) center = op
        }

        binding.txtInfo.text = buildInfo(r)

        center?.let {
            map.controller.setZoom(17.0)
            map.controller.setCenter(it)
        }
    }

    private fun buildPlaneSnippet(r: UasRecord): String = buildString {
        append(OdidParser.statusName(r.status))
        if (!r.speedHorizontal.isNaN()) append(" · ${String.format("%.1f", r.speedHorizontal)} m/s")
        if (!r.direction.isNaN()) append(" · 航向 ${String.format("%.0f", r.direction)}°")
        if (!r.altGeo.isNaN()) append(" · 高度 ${String.format("%.0f", r.altGeo)} m")
    }

    private fun buildInfo(r: UasRecord): String = buildString {
        append("机型：${OdidParser.uaTypeName(r.uaType)}　")
        append("ID：${r.uasId.ifBlank { "未收到" }}\n")
        append("类型：${OdidParser.idTypeName(r.idType)}\n")
        if (r.hasPosition()) {
            append("飞机：${String.format("%.6f, %.6f", r.latitude, r.longitude)}\n")
        } else {
            append("飞机位置：尚未收到\n")
        }
        if (r.hasOperator) {
            append("飞手：${String.format("%.6f, %.6f", r.operatorLat, r.operatorLon)}\n")
        }
        if (r.selfDesc.isNotBlank()) append("描述：${r.selfDesc}\n")
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    companion object {
        const val EXTRA_MAC = "extra_mac"
    }
}
