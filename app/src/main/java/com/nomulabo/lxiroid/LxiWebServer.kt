package com.nomulabo.lxiroid

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log
import fi.iki.elonen.NanoHTTPD

class LxiWebServer(
    port: Int,
    private val activity: MainActivity,
    private val webSocketServer: LxiWebSocketServer
) : NanoHTTPD(port), SensorEventListener {

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    override fun serve(session: IHTTPSession?): Response {
        val uri = session?.uri ?: "/"
        return when (uri) {
            "/lxi" -> newFixedLengthResponse("""
                <html><head><title>LXIroid</title></head><body>
                <h1>LXIroid</h1>
                <ul>
                <li><a href="/lxi/identification">/lxi/identification</a></li>
                <li><a href="/lxi/accel">/lxi/accel</a></li>
                <li><a href="/lxi/graph">/lxi/graph</a></li>
                </ul>
                </body></html>
            """)

            "/lxi/identification" -> newFixedLengthResponse("""
                <html><body><pre>${activity.generateDeviceId()}</pre></body></html>
            """)

            "/lxi/accel" -> {
                val (x, y, z) = getAccel()
                newFixedLengthResponse("""
                    <html><body>
                    <h1>Accelerometer</h1>
                    X: $x<br>
                    Y: $y<br>
                    Z: $z
                    </body></html>
                """)
            }

            "/lxi/graph" -> newFixedLengthResponse("""
                <html>
<head>
    <meta charset="utf-8">
    <title>Real-Time Acceleration Graph</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
</head>
<body>
    <h1>リアルタイム加速度グラフ</h1>
    <canvas id="accelChart" width="400" height="200"></canvas>
    <script>
        const ctx = document.getElementById('accelChart').getContext('2d');
        const chart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [
                    { label: 'X', data: [], borderColor: 'red', fill: false },
                    { label: 'Y', data: [], borderColor: 'green', fill: false },
                    { label: 'Z', data: [], borderColor: 'blue', fill: false }
                ]
            },
            options: {
                animation: false,
                responsive: true,
                scales: {
                    y: { min: -15, max: 15 }
                }
            }
        });

        const ws = new WebSocket(`ws://${"$"}{location.hostname}:8081`);

        ws.onmessage = (e) => {
            const d = JSON.parse(e.data);
            chart.data.labels.push('');
            chart.data.datasets[0].data.push(d.X);
            chart.data.datasets[1].data.push(d.Y);
            chart.data.datasets[2].data.push(d.Z);
            if (chart.data.labels.length > 50) {
                chart.data.labels.shift();
                chart.data.datasets.forEach(ds => ds.data.shift());
            }
            chart.update();
        };

        setInterval(() => {
            if (ws.readyState === WebSocket.OPEN) {
                ws.send("ping");
            }
        }, 3000);
    </script>
</body>
</html>
            """)

            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            lastX = it.values[0]
            lastY = it.values[1]
            lastZ = it.values[2]
            Thread {
                webSocketServer.broadcastAccel(lastX, lastY, lastZ)
            }.start()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun getAccel(): Triple<Float, Float, Float> {
        return Triple(lastX, lastY, lastZ)
    }
}
