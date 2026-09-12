package ir.nv.navigation.cloud

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** WebSocket transport for live incidents/traffic/POI invalidation. */
class NvRealtimeClient(
    private val client: OkHttpClient = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
) {
    interface Listener {
        fun onOpen() {}
        fun onMessage(text: String)
        fun onClosed() {}
        fun onFailure(error: Throwable) {}
    }

    fun connect(url: String, accessToken: String?, listener: Listener): WebSocket {
        require(url.startsWith("wss://") || url.startsWith("ws://")) { "WebSocket URL نامعتبر است" }
        val builder = Request.Builder().url(url)
        if (!accessToken.isNullOrBlank()) builder.header("Authorization", "Bearer $accessToken")
        return client.newWebSocket(builder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()
            override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(text)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed()
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.onFailure(t)
        })
    }
}
