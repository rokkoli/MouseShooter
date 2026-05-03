package de.rokkoli.mouseshooter

import kotlinx.browser.window

// ---------------------------------------------------------------------------
// JS NetworkManager — wraps PeerJS for P2P multiplayer connection
// ---------------------------------------------------------------------------

object JsMessageType {
    const val GAME_START   = "gameStart"
    const val PLAYER_INPUT = "playerInput"
    const val GAME_SYNC    = "gameSync"
    const val GAME_OVER    = "gameOver"
    const val SHOOT        = "shoot"
    const val PICKUP       = "pickup"
}

enum class JsConnectionState {
    Idle, WaitingForGuest, Connecting, Connected, Error,
}

class NetworkManager {
    var state: JsConnectionState = JsConnectionState.Idle
        private set

    var errorMessage: String? = null
        private set

    var roomCode: String = ""
        private set

    private var peer: JsPeer? = null
    private val connections = mutableMapOf<Int, JsDataConnection>()
    internal var hostConnection: JsDataConnection? = null

    val numConnections: Int get() = connections.size

    var onStateChanged: ((JsConnectionState) -> Unit)? = null
    var onMessageReceived: ((type: String, data: dynamic) -> Unit)? = null

    // ── Host ─────────────────────────────────────────────────────────────
    fun hostGame() {
        val code = generateRoomCode()
        roomCode = code
        val peerId = "MSHOOT-$code"
        updateState(JsConnectionState.WaitingForGuest)

        try { peer = JsPeer(peerId) } catch (e: Throwable) {
            updateState(JsConnectionState.Error)
            errorMessage = "Peer erstellen fehlgeschlagen: ${e.message}"
            return
        }

        peer!!.on("open") { _ -> console.log("Host peer registered: $peerId") }

        peer!!.on("connection") { conn ->
            val dataConn = conn.unsafeCast<JsDataConnection>()
            if (connections.size >= 3) {
                console.log("Max 4 Spieler, verbindung abgelehnt")
                dataConn.close()
                return@on
            }
            val playerIndex = connections.size + 1
            connections[playerIndex] = dataConn
            setupDataConnection(dataConn, playerIndex)
        }

        peer!!.on("error") { err ->
            val errMsg = err.asDynamic().type?.toString() ?: err.toString()
            console.log("PeerJS host error: $errMsg")
            errorMessage = "Verbindungsfehler: $errMsg"
            updateState(JsConnectionState.Error)
        }
    }

    // ── Join ─────────────────────────────────────────────────────────────
    fun joinGame(code: String) {
        roomCode = code.uppercase()
        val peerId = "MSHOOT-$roomCode"
        updateState(JsConnectionState.Connecting)

        try { peer = JsPeer() } catch (e: Throwable) {
            updateState(JsConnectionState.Error)
            errorMessage = "Peer erstellen fehlgeschlagen: ${e.message}"
            return
        }

        peer!!.on("open") { _ ->
            console.log("Guest peer open, connecting to: $peerId")
            val conn = peer!!.connect(peerId)
            hostConnection = conn
            setupDataConnection(conn, -1)
        }

        peer!!.on("error") { err ->
            val errMsg = err.asDynamic().type?.toString() ?: err.toString()
            console.log("PeerJS guest error: $errMsg")
            errorMessage = "Verbindungsfehler: $errMsg"
            updateState(JsConnectionState.Error)
        }
    }

    // ── Data channel ────────────────────────────────────────────────────
    private fun setupDataConnection(conn: JsDataConnection, playerIndex: Int) {
        conn.on("open") { _ ->
            console.log("Data channel open: Player $playerIndex")
            updateState(JsConnectionState.Connected)
        }

        conn.on("data") { data ->
            val type = data.type?.toString() ?: return@on
            if (playerIndex != -1) {
                data.playerIndex = playerIndex
            }
            onMessageReceived?.invoke(type, data)
        }

        conn.on("close") { _ ->
            console.log("Data channel closed: Player $playerIndex")
            if (playerIndex != -1) {
                connections.remove(playerIndex)
                if (connections.isEmpty()) updateState(JsConnectionState.WaitingForGuest)
            } else {
                updateState(JsConnectionState.Idle)
            }
        }

        conn.on("error") { err ->
            console.log("Data channel error: $err")
            errorMessage = "Datenkanal-Fehler: $err"
            updateState(JsConnectionState.Error)
        }
    }

    // ── Send ─────────────────────────────────────────────────────────────
    fun send(message: dynamic) {
        if (hostConnection != null) {
            hostConnection?.send(message)
        } else {
            connections.values.forEach { it.send(message) }
        }
    }

    fun sendGameStart(numPlayers: Int) {
        connections.forEach { (index, conn) ->
            val msg = js("{}")
            msg.type = JsMessageType.GAME_START
            msg.playerIndex = index
            msg.numPlayers = numPlayers
            conn.send(msg)
        }
    }

    fun sendPlayerInput(playerIndex: Int, data: PlayerInputData) {
        val msg = js("{}")
        msg.type = JsMessageType.PLAYER_INPUT
        msg.playerIndex = playerIndex
        msg.rotation = data.rotation
        msg.isMoving = data.isMoving
        msg.isRightDown = data.isRightDown
        msg.mouseOffsetX = data.mouseOffsetX
        msg.mouseOffsetY = data.mouseOffsetY
        msg.scrollDelta = data.scrollDelta
        send(msg)
    }

    fun sendGameSync(data: GameSyncData) {
        val msg = js("{}")
        msg.type = JsMessageType.GAME_SYNC
        msg.players = data.players.map { p ->
            val pObj = js("{}")
            pObj.id = p.id
            pObj.x = p.x
            pObj.y = p.y
            pObj.rotation = p.rotation
            pObj.hp = p.hp
            pObj.isAlive = p.isAlive
            pObj.isSpawning = p.isSpawning
            pObj.kills = p.kills
            pObj.selectedSlotIndex = p.selectedSlotIndex
            pObj.fireCooldown = p.fireCooldown
            pObj.velocityX = p.velocityX
            pObj.velocityY = p.velocityY
            pObj
        }.toTypedArray()
        msg.projectiles = data.projectiles.map { proj ->
            val pObj = js("{}")
            pObj.id = proj.id
            pObj.ownerId = proj.ownerId
            pObj.x = proj.x
            pObj.y = proj.y
            pObj.vx = proj.vx
            pObj.vy = proj.vy
            pObj.damage = proj.damage
            pObj.radius = proj.radius
            pObj.color = proj.color.toDouble()
            pObj.lifeTime = proj.lifeTime
            pObj.maxLifeTime = proj.maxLifeTime
            pObj.isExplosive = proj.isExplosive
            pObj.explosionRadius = proj.explosionRadius
            pObj
        }.toTypedArray()
        msg.gameTime = data.gameTime
        msg.battleZoneRadius = data.battleZoneRadius
        msg.isGameOver = data.isGameOver
        msg.winnerId = data.winnerId
        msg.killFeed = data.killFeed.toTypedArray()
        msg.timestamp = window.performance.now()
        send(msg)
    }

    fun sendGameOver(winnerId: Int) {
        val msg = js("{}")
        msg.type = JsMessageType.GAME_OVER
        msg.winnerId = winnerId
        send(msg)
    }

    fun sendShoot(playerIndex: Int) {
        val msg = js("{}")
        msg.type = JsMessageType.SHOOT
        msg.playerIndex = playerIndex
        send(msg)
    }

    fun sendPickup(playerIndex: Int) {
        val msg = js("{}")
        msg.type = JsMessageType.PICKUP
        msg.playerIndex = playerIndex
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
        updateState(JsConnectionState.Idle)
        errorMessage = null
        roomCode = ""
    }

    // ── Internal ────────────────────────────────────────────────────────
    private fun updateState(newState: JsConnectionState) {
        state = newState
        onStateChanged?.invoke(newState)
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..4).map { chars.random() }.joinToString("")
    }
}
