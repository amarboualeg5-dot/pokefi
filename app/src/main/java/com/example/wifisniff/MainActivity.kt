package com.gabriel.wifiscanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var adapter: MonsterAdapter
    private val monsters = mutableListOf<WifiMonster>()

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) scanSuccess()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Request location permission if needed (required for WiFi scanning)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        adapter = MonsterAdapter(monsters) { monster ->
            // Show capture toast
            Toast.makeText(this, "Gotcha! ${monster.name} caught!", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        val scanButton = findViewById<Button>(R.id.scanButton)
        scanButton.setOnClickListener { startScan() }

        registerReceiver(wifiScanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    private fun startScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission needed", Toast.LENGTH_SHORT).show()
            return
        }
        monsters.clear()
        val success = wifiManager.startScan()
        if (!success) {
            Toast.makeText(this, "Scan failed – try again", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanSuccess() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val results: List<ScanResult> = wifiManager.scanResults
        monsters.clear()
        for (result in results) {
            monsters.add(MonsterFactory.create(result))
        }
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wifiScanReceiver)
    }
}

// ---------- Monster classes (you can add these to the same file for simplicity) ----------

data class WifiMonster(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val security: String,
    val frequency: Int,
    val vendor: String,
    val name: String,
    val rarity: Rarity,
    val element: Element
)

enum class Rarity { COMMON, UNCOMMON, RARE, LEGENDARY }
enum class Element { EARTH, FIRE, LIGHTNING, NEUTRAL }

object MonsterFactory {
    fun create(result: ScanResult): WifiMonster {
        val ssid = result.SSID.ifEmpty { "<hidden>" }
        val rssi = result.level
        val freq = result.frequency
        val security = when {
            result.capabilities.contains("WPA3") -> "WPA3"
            result.capabilities.contains("WPA2") -> "WPA2"
            result.capabilities.contains("WPA") -> "WPA"
            result.capabilities.contains("WEP") -> "WEP"
            else -> "OPEN"
        }
        val vendor = result.BSSID.substring(0, 8).uppercase()
        val species = when {
            vendor.contains("Cisco", ignoreCase = true) -> "Ancient Router Dragon"
            vendor.contains("Apple", ignoreCase = true) -> "iBeast"
            vendor.contains("TP-Link", ignoreCase = true) -> "Forest Sprite"
            vendor.contains("Netgear", ignoreCase = true) -> "Net-Wyrm"
            ssid.contains("Starbucks", ignoreCase = true) -> "Caffeine Sprite"
            else -> "Signal Wisp"
        }
        val rarity = when (security) {
            "OPEN" -> Rarity.COMMON
            "WEP" -> Rarity.UNCOMMON
            "WPA", "WPA2" -> Rarity.RARE
            "WPA3" -> Rarity.LEGENDARY
            else -> Rarity.COMMON
        }
        val element = when {
            freq in 2400..2499 -> Element.EARTH
            freq in 5000..5899 -> Element.FIRE
            freq >= 5900 -> Element.LIGHTNING
            else -> Element.NEUTRAL
        }
        val prefixes = listOf("Mega", "Shadow", "Alpha", "Crystal", "Phantom")
        val prefix = if (rarity == Rarity.LEGENDARY) prefixes.random() else ""
        val name = "$prefix $species".trim()

        return WifiMonster(
            bssid = result.BSSID,
            ssid = ssid,
            rssi = rssi,
            security = security,
            frequency = freq,
            vendor = vendor,
            name = name,
            rarity = rarity,
            element = element
        )
    }
}

// Simple adapter – displays monster name and SSID
class MonsterAdapter(
    private val list: List<WifiMonster>,
    private val onClick: (WifiMonster) -> Unit
) : RecyclerView.Adapter<MonsterAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val m = list[position]
        holder.textView.text = "${m.name} (${m.ssid}) – ${m.rarity.name}"
        holder.itemView.setOnClickListener { onClick(m) }
    }

    override fun getItemCount(): Int = list.size
}
