package com.nomulabo.lxiroid

import android.util.Log
import fi.iki.elonen.NanoWSD
import java.io.IOException
import java.net.InetSocketAddress

class LxiWebSocketServer(port: Int) : NanoWSD(port) {

    private val clients = mutableSetOf<WebSocket>()

    override fun openWebSocket(handshake: IHTTPSession?): WebSocket {
        val socket = AccelSocket(handshake)
        clients.add(socket)
        return socket
    }

    fun broadcastAccel(x: Float, y: Float, z: Float) {
        val data = """{"X":$x,"Y":$y,"Z":$z}"""
        for (client in clients) {
            try {
                if (client.isOpen) {
                    client.send(data)
                }
            } catch (e: IOException) {
                Log.e("WebSocket", "送信失敗: ${e.message}")
            }
        }
    }

    inner class AccelSocket(handshakeRequest: IHTTPSession?) : WebSocket(handshakeRequest) {
        override fun onOpen() {
            Log.d("WebSocket", "クライアント接続")
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            Log.d("WebSocket", "切断: $reason")
            clients.remove(this)
        }

        override fun onMessage(message: WebSocketFrame?) {
            // pingなどが来たとき用（現状何もしない）
        }

        override fun onPong(pong: WebSocketFrame?) {}

        override fun onException(exception: IOException?) {
            Log.e("WebSocket", "例外: ${exception?.message}")
        }
    }
}
