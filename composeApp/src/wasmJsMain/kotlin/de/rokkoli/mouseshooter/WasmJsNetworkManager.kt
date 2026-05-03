@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package de.rokkoli.mouseshooter

// ---------------------------------------------------------------------------
// WasmJS NetworkManager — wraps PeerJS for P2P multiplayer
// ---------------------------------------------------------------------------

// Helper JS interop functions for WasmJS
@JsFun("function() { return {}; }")
internal external fun createJsObject(): JsAny

@JsFun("function(obj, key, value) { obj[key] = value; }")
internal external fun setJsString(obj: JsAny, key: String, value: String)

@JsFun("function(obj, key, value) { obj[key] = value; }")
internal external fun setJsFloat(obj: JsAny, key: String, value: Float)

@JsFun("function(obj, key, value) { obj[key] = value; }")
internal external fun setJsBoolean(obj: JsAny, key: String, value: Boolean)

@JsFun("function(obj, key, value) { obj[key] = value; }")
internal external fun setJsInt(obj: JsAny, key: String, value: Int)

@JsFun("function(obj, key, value) { obj[key] = value; }")
internal external fun setJsDouble(obj: JsAny, key: String, value: Double)

@JsFun("function(obj, key, value) { obj[key] = value; }")
internal external fun setJsAny(obj: JsAny, key: String, value: JsAny)

@JsFun("function() { return []; }")
internal external fun createJsArray(): JsAny

@JsFun("function(arr, item) { arr.push(item); }")
internal external fun pushJsArray(arr: JsAny, item: JsAny)

@JsFun("function(arr) { return arr.length; }")
internal external fun getJsArrayLength(arr: JsAny): Int

@JsFun("function(arr, index) { return arr[index]; }")
internal external fun getJsArrayItem(arr: JsAny, index: Int): JsAny

@JsFun("function(obj, key) { return obj[key]; }")
internal external fun getJsArray(obj: JsAny, key: String): JsAny

@JsFun("function(obj, key) { return obj[key] != null ? String(obj[key]) : null; }")
internal external fun getJsString(obj: JsAny, key: String): String?

@JsFun("function(obj, key) { return obj[key]; }")
internal external fun getJsFloat(obj: JsAny, key: String): Float

@JsFun("function(obj, key) { return obj[key]; }")
internal external fun getJsBoolean(obj: JsAny, key: String): Boolean

@JsFun("function(obj, key) { return obj[key]; }")
internal external fun getJsInt(obj: JsAny, key: String): Int

@JsFun("function(obj, key) { return Number(obj[key]); }")
internal external fun getJsLong(obj: JsAny, key: String): Double

@JsFun("function() { return window.performance.now(); }")
internal external fun performanceNow(): Float

@JsFun("function(message) { console.log(message); }")
internal external fun consoleLog(message: String)

@JsFun("function(obj) { return obj.type ? obj.type.toString() : obj.toString(); }")
internal external fun getErrorString(obj: JsAny): String

object WasmMessageType {
    const val GAME_START   = "gameStart"
    const val PLAYER_INPUT = "playerInput"
    const val GAME_SYNC    = "gameSync"
    const val GAME_OVER    = "gameOver"
    const val SHOOT        = "shoot"
    const val PICKUP       = "pickup"
}

enum class WasmConnectionState {
    Idle, WaitingForGuest, Connecting, Connected, Error,
}

class WasmJsNetworkManager {
    var state: WasmConnectionState = WasmConnectionState.Idle
        private set

    var errorMessage: String? = null
        private set

    var roomCode: String = ""
        private set

    val numConnections: Int get() = connections.size
    val isGuest: Boolean get() = hostConnection != null

    private var peer: WasmJsPeer? = null
    internal val connections = mutableMapOf<Int, WasmJsDataConnection>()
    internal var hostConnection: WasmJsDataConnection? = null

    var onStateChanged: ((WasmConnectionState) -> Unit)? = null
    var onMessageReceived: ((type: String, data: JsAny) -> Unit)? = null

    // ── Host ─────────────────────────────────────────────────────────────
    fun hostGame() {
        val code = generateRoomCode()
        roomCode = code
        val peerId = "MSHOOT-$code"
        updateState(WasmConnectionState.WaitingForGuest)

        try { peer = WasmJsPeer(peerId) } catch (e: Throwable) {
            updateState(WasmConnectionState.Error)
            errorMessage = "Peer erstellen fehlgeschlagen: ${e.message}"
            return
        }

        peer!!.on("open") { _ -> consoleLog("Host peer registered: $peerId") }

        peer!!.on("connection") { connAny ->
            val dataConn = connAny!!.unsafeCast<WasmJsDataConnection>()
            if (connections.size >= 3) {
                consoleLog("Max 4 Spieler, verbindung abgelehnt")
                dataConn.close()
                return@on
            }
            val playerIndex = connections.size + 1
            connections[playerIndex] = dataConn
            setupDataConnection(dataConn, playerIndex)
        }

        peer!!.on("error") { errAny ->
            val errStr = if (errAny != null) getErrorString(errAny) else "Unknown error"
            consoleLog("PeerJS host error: $errStr")
            errorMessage = "Verbindungsfehler: $errStr"
            updateState(WasmConnectionState.Error)
        }
    }

    // ── Join ─────────────────────────────────────────────────────────────
    fun joinGame(code: String) {
        roomCode = code.uppercase()
        val peerId = "MSHOOT-$roomCode"
        updateState(WasmConnectionState.Connecting)

        try { peer = WasmJsPeer() } catch (e: Throwable) {
            updateState(WasmConnectionState.Error)
            errorMessage = "Peer erstellen fehlgeschlagen: ${e.message}"
            return
        }

        peer!!.on("open") { _ ->
            consoleLog("Guest peer open, connecting to: $peerId")
            val conn = peer!!.connect(peerId)
            hostConnection = conn
            setupDataConnection(conn, -1)
        }

        peer!!.on("error") { errAny ->
            val errStr = if (errAny != null) getErrorString(errAny) else "Unknown error"
            consoleLog("PeerJS guest error: $errStr")
            errorMessage = "Verbindungsfehler: $errStr"
            updateState(WasmConnectionState.Error)
        }
    }

    // ── Data channel ────────────────────────────────────────────────────
    private fun setupDataConnection(conn: WasmJsDataConnection, playerIndex: Int) {
        conn.on("open") { _ ->
            consoleLog("Data channel open: Player $playerIndex")
            updateState(WasmConnectionState.Connected)
        }

        conn.on("data") { dataAny ->
            if (dataAny == null) return@on
            val type = getJsString(dataAny, "type") ?: return@on
            if (playerIndex != -1) {
                setJsInt(dataAny, "playerIndex", playerIndex)
            }
            onMessageReceived?.invoke(type, dataAny)
        }

        conn.on("close") { _ ->
            consoleLog("Data channel closed: Player $playerIndex")
            if (playerIndex != -1) {
                connections.remove(playerIndex)
                if (connections.isEmpty()) updateState(WasmConnectionState.WaitingForGuest)
            } else {
                updateState(WasmConnectionState.Idle)
            }
        }

        conn.on("error") { errAny ->
            val errStr = if (errAny != null) getErrorString(errAny) else "Unknown error"
            consoleLog("Data channel error: $errStr")
            errorMessage = "Datenkanal-Fehler: $errStr"
            updateState(WasmConnectionState.Error)
        }
    }

    // ── Send ─────────────────────────────────────────────────────────────
    fun send(message: JsAny) {
        if (hostConnection != null) {
            hostConnection?.send(message)
        } else {
            connections.values.forEach { it.send(message) }
        }
    }

    fun sendGameStart(numPlayers: Int) {
        connections.forEach { (index, conn) ->
            val msg = createJsObject()
            setJsString(msg, "type", WasmMessageType.GAME_START)
            setJsInt(msg, "playerIndex", index)
            setJsInt(msg, "numPlayers", numPlayers)
            conn.send(msg)
        }
    }

    fun sendPlayerInput(playerIndex: Int, data: PlayerInputData) {
        val msg = createJsObject()
        setJsString(msg, "type", WasmMessageType.PLAYER_INPUT)
        setJsInt(msg, "playerIndex", playerIndex)
        setJsFloat(msg, "rotation", data.rotation)
        setJsBoolean(msg, "isMoving", data.isMoving)
        setJsBoolean(msg, "isRightDown", data.isRightDown)
        setJsFloat(msg, "mouseOffsetX", data.mouseOffsetX)
        setJsFloat(msg, "mouseOffsetY", data.mouseOffsetY)
        setJsFloat(msg, "scrollDelta", data.scrollDelta)
        send(msg)
    }

    fun sendGameSync(players: List<PlayerSyncData>, projectiles: List<ProjectileSyncData>,
                     gameTime: Float, battleZoneRadius: Float, isGameOver: Boolean, winnerId: Int,
                     killFeed: List<String>) {
        val msg = createJsObject()
        setJsString(msg, "type", WasmMessageType.GAME_SYNC)

        val playersArr = createJsArray()
        players.forEach { p ->
            val pObj = createJsObject()
            setJsInt(pObj, "id", p.id)
            setJsFloat(pObj, "x", p.x)
            setJsFloat(pObj, "y", p.y)
            setJsFloat(pObj, "rotation", p.rotation)
            setJsFloat(pObj, "hp", p.hp)
            setJsBoolean(pObj, "isAlive", p.isAlive)
            setJsBoolean(pObj, "isSpawning", p.isSpawning)
            setJsInt(pObj, "kills", p.kills)
            setJsInt(pObj, "selectedSlotIndex", p.selectedSlotIndex)
            setJsFloat(pObj, "fireCooldown", p.fireCooldown)
            setJsFloat(pObj, "velocityX", p.velocityX)
            setJsFloat(pObj, "velocityY", p.velocityY)
            pushJsArray(playersArr, pObj)
        }
        setJsAny(msg, "players", playersArr)

        val projArr = createJsArray()
        projectiles.forEach { proj ->
            val pObj = createJsObject()
            setJsInt(pObj, "id", proj.id)
            setJsInt(pObj, "ownerId", proj.ownerId)
            setJsFloat(pObj, "x", proj.x)
            setJsFloat(pObj, "y", proj.y)
            setJsFloat(pObj, "vx", proj.vx)
            setJsFloat(pObj, "vy", proj.vy)
            setJsFloat(pObj, "damage", proj.damage)
            setJsFloat(pObj, "radius", proj.radius)
            setJsDouble(pObj, "color", proj.color.toDouble())
            setJsFloat(pObj, "lifeTime", proj.lifeTime)
            setJsFloat(pObj, "maxLifeTime", proj.maxLifeTime)
            setJsBoolean(pObj, "isExplosive", proj.isExplosive)
            setJsFloat(pObj, "explosionRadius", proj.explosionRadius)
            pushJsArray(projArr, pObj)
        }
        setJsAny(msg, "projectiles", projArr)

        setJsFloat(msg, "gameTime", gameTime)
        setJsFloat(msg, "battleZoneRadius", battleZoneRadius)
        setJsBoolean(msg, "isGameOver", isGameOver)
        setJsInt(msg, "winnerId", winnerId)

        val killFeedArr = createJsArray()
        killFeed.forEach { kf ->
            val kfObj = createJsObject()
            setJsString(kfObj, "text", kf)
            pushJsArray(killFeedArr, kfObj)
        }
        setJsAny(msg, "killFeed", killFeedArr)

        setJsFloat(msg, "timestamp", performanceNow())
        send(msg)
    }

    fun sendGameOver(winnerId: Int) {
        val msg = createJsObject()
        setJsString(msg, "type", WasmMessageType.GAME_OVER)
        setJsInt(msg, "winnerId", winnerId)
        send(msg)
    }

    fun sendShoot(playerIndex: Int) {
        val msg = createJsObject()
        setJsString(msg, "type", WasmMessageType.SHOOT)
        setJsInt(msg, "playerIndex", playerIndex)
        send(msg)
    }

    fun sendPickup(playerIndex: Int) {
        val msg = createJsObject()
        setJsString(msg, "type", WasmMessageType.PICKUP)
        setJsInt(msg, "playerIndex", playerIndex)
        send(msg)
    }

    // ── Cleanup ─────────────────────────────────────────────────────────
    fun disconnect() {
        hostConnection?.close()
        hostConnection = null
        connections.values.toList().forEach { it.close() }
        connections.clear()
        peer?.destroy()
        peer = null
        updateState(WasmConnectionState.Idle)
        errorMessage = null
        roomCode = ""
    }

    // ── Internal ────────────────────────────────────────────────────────
    private fun updateState(newState: WasmConnectionState) {
        state = newState
        onStateChanged?.invoke(newState)
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..4).map { chars.random() }.joinToString("")
    }
}
