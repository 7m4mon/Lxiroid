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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class MainActivity : AppCompatActivity() {

    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var webServer: LxiWebServer
    private lateinit var webSocketServer: LxiWebSocketServer
    private var jmDNS: JmDNS? = null

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

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!

        webSocketServer = LxiWebSocketServer(8081)
        webSocketServer.start()

        webServer = LxiWebServer(8080, this, webSocketServer)
        sensorManager.registerListener(webServer, accelerometer, SensorManager.SENSOR_DELAY_UI)
        webServer.start()

        val scpiServer = ScpiSocketServer(
            onClientConnected = {
                runOnUiThread { statusText.text = "Status: REMOTE" }
            },
            onClientDisconnected = {
                runOnUiThread { statusText.text = "Status: IDLE" }
            },
            onBeep = {
                runOnUiThread { beep() }
            },
            getDeviceId = { generateDeviceId() },
            getAccel = { webServer.getAccel() }
        )

        scpiServer.start()
        startMdnsService(5025)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(webServer)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(webServer, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            sensorManager.unregisterListener(webServer)
        } catch (e: Exception) {
            Log.w("Sensor", "Failed to unregister listener", e)
        }
        try {
            webServer.stop()
        } catch (e: Exception) {
            Log.w("WebServer", "Failed to stop web server", e)
        }
        try {
            webSocketServer.stop()
        } catch (e: Exception) {
            Log.w("WebSocket", "Failed to stop WebSocket server", e)
        }
        try {
            jmDNS?.unregisterAllServices()
            jmDNS?.close()
        } catch (e: Exception) {
            Log.w("mDNS", "Failed to stop jmDNS", e)
        }
    }

    fun beep() {
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
    }

    fun generateDeviceId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun startMdnsService(port: Int) {
        Thread {
            try {
                Log.d("jmDNS", "Start")
                val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                val ip = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
                val inetAddress = InetAddress.getByName(ip)

                jmDNS = JmDNS.create(inetAddress)

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
                    "_scpi-raw._tcp.local.",
                    name,
                    port,
                    0, 0,
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
