package com.nomulabo.lxiroid

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.widget.TextView
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings

import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import java.net.NetworkInterface
import java.net.InetAddress

import android.os.Build

import android.util.Log
import com.nomulabo.lxiroid.LxiWebServer


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // IPアドレスを取得して表示
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val ipAddress = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        val ipText = findViewById<TextView>(R.id.textIp)
        ipText.text = "IP: $ipAddress"

        // 状態表示用TextView
        val statusText = findViewById<TextView>(R.id.textStatus)


        val scpiServer = ScpiSocketServer(
            onClientConnected = {
                runOnUiThread { statusText.text = "Status: REMOTE" }
            },
            onClientDisconnected = {
                runOnUiThread { statusText.text = "Status: IDLE" }
            },
            onBeep = {
                runOnUiThread { beep() } // UIスレッドで音を鳴らす
            },
            getDeviceId = { generateDeviceId() }
        )

        scpiServer.start()
        startMdnsService(5025)
        val webServer = LxiWebServer(8080, this)
        webServer.start()

        // インセット調整（パディング設定）
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // ここに追加！
    fun beep() {
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150) // 150ms ピッ音
    }

    fun generateDeviceId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    var jmDNS: JmDNS? = null

    fun startMdnsService(port: Int) {
        Thread {
            try {
                Log.d("jmDNS", "Start")
                val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                val ip = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
                val inetAddress = InetAddress.getByName(ip)

                jmDNS = JmDNS.create(inetAddress)

                // 衝突回避つきサービス名
                val baseName = "LXIroid"
                var name = baseName
                var counter = 1
                while (jmDNS!!.getServiceInfo("_scpi-raw._tcp.local.", name) != null) {
                    name = "$baseName-$counter"
                    counter++
                }

                val txtRecord = mapOf(
                    "txtvers" to "1",
                    "Manufacturer" to Build.MANUFACTURER,
                    "Model" to Build.MODEL,
                    "SerialNumber" to generateDeviceId(),
                    "FirmwareVersion" to Build.ID,
                    "Address" to "TCPIP::${Build.MODEL}.local::5025::SOCKET"
                )

                val serviceInfo = ServiceInfo.create(
                    "_scpi-raw._tcp.local.", // サービスタイプ
                    name,
                    port,
                    0, 0, // priority, weight（通常は0）
                    txtRecord
                )

                jmDNS!!.registerService(serviceInfo)

                Log.d("mDNS", "Service registered: $name on $port")
            } catch (e: Exception) {
                Log.e("mDNS", "Error starting mDNS", e)
            }
        }.start()
    }
}