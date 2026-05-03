@file:JsModule("peerjs")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package de.rokkoli.mouseshooter

// ---------------------------------------------------------------------------
// Kotlin/WasmJS external declarations for PeerJS
// ---------------------------------------------------------------------------

@JsName("Peer")
external class WasmJsPeer : JsAny {
    constructor()
    constructor(id: String)
    constructor(id: String, options: JsAny)

    val id: String
    val open: Boolean
    val disconnected: Boolean
    val destroyed: Boolean

    fun connect(id: String): WasmJsDataConnection
    fun connect(id: String, options: JsAny): WasmJsDataConnection
    fun on(event: String, callback: (JsAny?) -> Unit)
    fun disconnect()
    fun destroy()
    fun reconnect()
}

external interface WasmJsDataConnection : JsAny {
    val peer: String
    val open: Boolean
    val label: String
    val metadata: JsAny?

    fun send(data: JsAny)
    fun close()
    fun on(event: String, callback: (JsAny?) -> Unit)
}
