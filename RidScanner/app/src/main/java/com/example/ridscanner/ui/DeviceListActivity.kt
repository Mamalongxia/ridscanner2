package com.example.ridscanner.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ridscanner.data.UasRecord
import com.example.ridscanner.databinding.ActivityDeviceListBinding
import com.example.ridscanner.ble.RidScanManager

class DeviceListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceListBinding
    private lateinit var scanManager: RidScanManager
    private val adapter = DeviceAdapter(::openMap)

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        scanManager = RidScanManager(this)
        scanManager.onUpdate = { list ->
            adapter.submit(list)
            binding.txtCount.text = "${list.size} 台"
            val has = list.isNotEmpty()
            binding.recycler.visibility = if (has) android.view.View.VISIBLE else android.view.View.GONE
            binding.emptyHint.visibility = if (has) android.view.View.GONE else android.view.View.VISIBLE
        }

        binding.btnScan.setOnClickListener {
            if (scanManager.scanning) {
                scanManager.stop()
                binding.btnScan.setText(com.example.ridscanner.R.string.action_scan_on)
            } else {
                if (!ensurePermissions()) return@setOnClickListener
                val ok = scanManager.start()
                if (ok) {
                    binding.btnScan.setText(com.example.ridscanner.R.string.action_scan_off)
                } else {
                    Toast.makeText(this, "蓝牙未开启或不可用，请打开蓝牙后重试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun ensurePermissions(): Boolean {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true
        ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERM)
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERM && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            binding.btnScan.performClick()
        } else {
            Toast.makeText(this, "需要蓝牙/定位权限才能扫描 RID", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMap(record: UasRecord) {
        val intent = Intent(this, MapActivity::class.java).apply {
            putExtra(MapActivity.EXTRA_MAC, record.mac)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        scanManager.stop()
        super.onDestroy()
    }

    companion object {
        private const val REQ_PERM = 101
    }
}
