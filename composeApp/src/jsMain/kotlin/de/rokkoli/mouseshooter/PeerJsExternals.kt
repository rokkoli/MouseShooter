@file:JsModule("peerjs")
@file:JsNonModule

package de.rokkoli.mouseshooter

// ---------------------------------------------------------------------------
// Kotlin external declarations for PeerJS (JS target)
// ---------------------------------------------------------------------------

@JsName("Peer")
external class JsPeer {
    constructor()
    constructor(id: String)
    constructor(id: String, options: dynamic)

    val id: String
    val open: Boolean
    val disconnected: Boolean
    val destroyed: Boolean

    fun connect(id: String): JsDataConnection
    fun connect(id: String, options: dynamic): JsDataConnection
    fun on(event: String, callback: (dynamic) -> Unit)
    fun disconnect()
    fun destroy()
    fun reconnect()
}

external interface JsDataConnection {
    val peer: String
    val open: Boolean
    val label: String
    val metadata: dynamic

    fun send(data: dynamic)
    fun close()
    fun on(event: String, callback: (dynamic) -> Unit)
}
