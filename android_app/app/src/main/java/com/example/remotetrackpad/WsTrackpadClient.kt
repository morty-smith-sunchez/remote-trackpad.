package com.example.remotetrackpad

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

class WsTrackpadClient(
    private val onState: (State) -> Unit,
) {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    private val http = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var state: State = State.DISCONNECTED

    fun connect(host: String, port: Int) {
        disconnect()
        state = State.CONNECTING
        onState(state)
        val req = Request.Builder()
            .url("ws://$host:$port/ws")
            .build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                state = State.CONNECTED
                onState(state)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                state = State.DISCONNECTED
                onState(state)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                state = State.DISCONNECTED
                onState(state)
            }

            override fun onMessage(webSocket: WebSocket, text: String) = Unit
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = Unit
        })
    }

    fun disconnect() {
        ws?.close(1000, "bye")
        ws = null
        if (state != State.DISCONNECTED) {
            state = State.DISCONNECTED
            onState(state)
        }
    }

    fun sendJson(json: String) {
        ws?.send(json)
    }
}

