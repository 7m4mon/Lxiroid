package com.nomulabo.lxiroid

import android.util.Log
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class ScpiSocketServer(
    private val onClientConnected: () -> Unit,
    private val onClientDisconnected: () -> Unit,
    private val onBeep: () -> Unit,
    private val getDeviceId: () -> String,
    private val getAccel: () -> Triple<Float, Float, Float>
) : Thread() {

    override fun run() {
        try {
            val serverSocket = ServerSocket(5025)
            Log.d("SCPI", "SCPI Server started on port ${serverSocket.localPort}")

            while (true) {
                val client = serverSocket.accept()
                Log.d("SCPI", "Client accepted: ${client.inetAddress.hostAddress}")

                Thread {
                    handleClient(client)
                }.start()
            }
        } catch (e: Exception) {
            Log.e("SCPI", "Server error", e)
        }
    }

    private fun handleClient(client: Socket) {
        onClientConnected()

        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream()))

            var command: String?
            while (reader.readLine().also { command = it } != null) {
                val trimmed = command?.trim()
                Log.d("SCPI", "Received: $trimmed")

                when (trimmed?.uppercase()) {
                    "*IDN?" -> {
                        val idn = "LXIroid,nomulabo,${getDeviceId()},1.0"
                        writer.write("$idn\n")
                        writer.flush()
                    }

                    "*BEEP" -> {
                        writer.write("BEEP OK\n")
                        writer.flush()
                        onBeep()
                    }

                    "*ACCEL?" -> {
                        val (x, y, z) = getAccel()
                        writer.write("X=%.2f,Y=%.2f,Z=%.2f\n".format(x, y, z))
                        writer.flush()
                    }

                    else -> {
                        writer.write("ERROR: Unknown command\n")
                        writer.flush()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("SCPI", "Client error", e)
        } finally {
            try {
                client.close()
            } catch (e: IOException) {
                Log.e("SCPI", "Error closing client", e)
            }
            onClientDisconnected()
            Log.d("SCPI", "Client disconnected")
        }
    }
}