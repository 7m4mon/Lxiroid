package com.nomulabo.lxiroid

import android.util.Log
import java.io.*
import java.net.ServerSocket
import java.net.SocketException
import java.net.Socket
import android.os.Build



class ScpiSocketServer(
    private val onClientConnected: () -> Unit,
    private val onClientDisconnected: () -> Unit,
    private val onBeep: () -> Unit,
    private val getDeviceId: () -> String // ← 追加
) : Thread() {

    override fun run() {
        try {
            val serverSocket = ServerSocket(5025)
            Log.d("SCPI", "SCPI Server started on port ${serverSocket.localPort}")

            while (true) {
                val client = serverSocket.accept()
                Log.d("SCPI", "Client accepted: ${client.inetAddress.hostAddress}")

                // クライアントごとにスレッドを分離
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
                        val manufacturer = Build.MANUFACTURER
                        val model = Build.MODEL
                        val serialNumber = getDeviceId() // ← ここで呼び出し
                        val buildId = Build.ID

                        val deviceInfo = "$model,$manufacturer,$serialNumber,$buildId"
                        writer.write("$deviceInfo\n")
                        writer.flush()
                    }

                    "*BEEP" -> {
                        writer.write("BEEP OK\n")
                        writer.flush()
                        onBeep() // ← ここで MainActivity の beep() を呼ぶ！
                    }

                    else -> {
                        writer.write("ERROR: Unknown command\n")
                        writer.flush()
                    }
                }
            }

        } catch (e: SocketException) {
            Log.i("SCPI", "Client disconnected cleanly: ${e.message}")
            // 接続終了として扱ってOK
            onClientDisconnected()
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
