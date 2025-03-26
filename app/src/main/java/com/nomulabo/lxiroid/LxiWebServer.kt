package com.nomulabo.lxiroid

import fi.iki.elonen.NanoHTTPD
import android.os.Build
import android.provider.Settings
import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class LxiWebServer(private val port: Int, private val context: Context) : NanoHTTPD(port), SensorEventListener {

    private var accelX: Float = 0f
    private var accelY: Float = 0f
    private var accelZ: Float = 0f

    init {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/lxi" -> newFixedLengthResponse(getWelcomePage())
            "/lxi/identification" -> newFixedLengthResponse(getIdentificationJson())
            "/lxi/accel" -> newFixedLengthResponse(getAccelJson())
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    private fun getWelcomePage(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val serial = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val firmware = Build.ID
        val hostname = "$model.local"
        val ip = getDeviceIpAddress()

        return """
            <html>
            <head><title>LXIroid Web UI</title></head>
            <body>
                <h1>LXIroid</h1>
                <p>Manufacturer: $manufacturer</p>
                <p>Model: $model</p>
                <p>Serial Number: $serial</p>
                <p>Firmware Version: $firmware</p>
                <p>LXI Version: 1.6</p>
                <p>Hostname: $hostname</p>
                <p>IP Address: $ip</p>
                <p>VISA Address: TCPIP::$hostname::5025::SOCKET</p>
                <h2>Accelerometer</h2>
                <p>X: $accelX</p>
                <p>Y: $accelY</p>
                <p>Z: $accelZ</p>
            </body>
            </html>
        """.trimIndent()
    }

    private fun getIdentificationJson(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val serial = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val firmware = Build.ID
        val hostname = "$model.local"
        val ip = getDeviceIpAddress()

        return """
            {
              "Manufacturer": "$manufacturer",
              "Model": "$model",
              "SerialNumber": "$serial",
              "FirmwareVersion": "$firmware",
              "Hostname": "$hostname",
              "MACAddress": "xx:xx:xx:xx:xx:xx",
              "IPAddress": "$ip",
              "LXI": {
                "Version": "1.6",
                "ExtendedFunctions": []
              }
            }
        """.trimIndent()
    }

    private fun getAccelJson(): String {
        return """
            {
              "Accelerometer": {
                "X": $accelX,
                "Y": $accelY,
                "Z": $accelZ
              }
            }
        """.trimIndent()
    }

    private fun getDeviceIpAddress(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            accelX = event.values[0]
            accelY = event.values[1]
            accelZ = event.values[2]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 不要なら空でOK
    }
}
