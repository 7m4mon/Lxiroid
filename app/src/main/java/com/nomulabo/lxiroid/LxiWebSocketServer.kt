package com.nomulabo.lxiroid

import android.content.Context
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocketFrame
import java.io.IOException


class LxiWebSocketServer(port: Int, val context: Context) : NanoWSD(port) {

    private val clients = mutableListOf<AccelSocket>()

    override fun openWebSocket(handshake: IHTTPSession?): WebSocket {
        val socket = AccelSocket(handshake)
        clients.add(socket)
        return socket
    }

    inner class AccelSocket(handshake: IHTTPSession?) : WebSocket(handshake) {
        override fun onOpen() {
            // 初回接続時の処理があればここに
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            clients.remove(this)
        }

        override fun onMessage(message: WebSocketFrame?) {
            // クライアントからのメッセージ受信（使わなければ空でOK）
        }

        override fun onPong(pong: WebSocketFrame?) {}

        override fun onException(exception: IOException?) {
            exception?.printStackTrace()
        }
    }

    fun broadcastAccel(x: Float, y: Float, z: Float) {
        val message = """{"X":$x,"Y":$y,"Z":$z}"""
        clients.forEach {
            it.send(message)
        }
    }
}
