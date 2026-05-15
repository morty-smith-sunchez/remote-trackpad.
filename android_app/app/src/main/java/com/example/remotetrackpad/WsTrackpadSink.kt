package com.example.remotetrackpad

import org.json.JSONObject

/** Sends JSON events to `pc_agent/server.py`. */
class WsTrackpadSink(private val client: WsTrackpadClient) : TrackpadSink {

    override fun move(dx: Float, dy: Float) {
        send("move", JSONObject().put("dx", dx.toDouble()).put("dy", dy.toDouble()))
    }

    override fun scroll(dy: Float) {
        send("scroll", JSONObject().put("dy", dy.toDouble()))
    }

    override fun scroll(dx: Float, dy: Float) {
        send(
            "scroll",
            JSONObject().put("dx", dx.toDouble()).put("dy", dy.toDouble()),
        )
    }

    override fun click(button: TrackpadSink.Button) {
        send("click", JSONObject().put("button", button.toJson()))
    }

    override fun down(button: TrackpadSink.Button) {
        send("down", JSONObject().put("button", button.toJson()))
    }

    override fun up(button: TrackpadSink.Button) {
        send("up", JSONObject().put("button", button.toJson()))
    }

    override fun keyTap(key: Byte, modifiers: Byte) {
        send(
            "key",
            JSONObject()
                .put("key", key.toInt() and 0xFF)
                .put("modifiers", modifiers.toInt() and 0xFF),
        )
    }

    override fun hotkey(modifiers: Byte, key: Byte) {
        send(
            "hotkey",
            JSONObject()
                .put("modifiers", modifiers.toInt() and 0xFF)
                .put("key", key.toInt() and 0xFF),
        )
    }

    override fun typeText(text: String) {
        if (text.isEmpty()) return
        send("text", JSONObject().put("text", text))
    }

    private fun send(type: String, body: JSONObject) {
        client.sendJson(body.put("type", type).toString())
    }

    private fun TrackpadSink.Button.toJson(): String = when (this) {
        TrackpadSink.Button.LEFT -> "left"
        TrackpadSink.Button.RIGHT -> "right"
        TrackpadSink.Button.MIDDLE -> "middle"
    }
}
