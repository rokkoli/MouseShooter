package de.rokkoli.mouseshooter

// ---------------------------------------------------------------------------
// JS implementation of MultiplayerConnector — wraps NetworkManager (PeerJS)
// ---------------------------------------------------------------------------

actual fun createMultiplayerConnector(): MultiplayerConnector = JsMultiplayerConnector()

class JsMultiplayerConnector : MultiplayerConnector() {

    private val network = NetworkManager()

    override var state: LobbyConnectionState = LobbyConnectionState.Idle
        private set

    override var errorMessage: String? = null
        private set

    override var roomCode: String = ""
        private set

    override val connectedPlayers: Int
        get() = if (network.hostConnection != null) 2 else network.numConnections + 1

    private var stateCallback: ((LobbyConnectionState) -> Unit)? = null
    private var gameStartCallback: ((Int, Int) -> Unit)? = null
    private var playerInputCallback: ((Int, PlayerInputData) -> Unit)? = null
    private var gameSyncCallback: ((GameSyncData) -> Unit)? = null
    private var gameOverCallback: ((Int) -> Unit)? = null
    private var shootCallback: ((Int) -> Unit)? = null
    private var pickupCallback: ((Int) -> Unit)? = null

    init {
        network.onStateChanged = { connState ->
            state = when (connState) {
                JsConnectionState.Idle -> LobbyConnectionState.Idle
                JsConnectionState.WaitingForGuest -> LobbyConnectionState.WaitingForGuest
                JsConnectionState.Connecting -> LobbyConnectionState.Connecting
                JsConnectionState.Connected -> LobbyConnectionState.Connected
                JsConnectionState.Error -> LobbyConnectionState.Error
            }
            errorMessage = network.errorMessage
            stateCallback?.invoke(state)
        }

        network.onMessageReceived = { type, data ->
            when (type) {
                JsMessageType.GAME_START -> {
                    val idx = (data.playerIndex as Number).toInt()
                    val numPlayers = (data.numPlayers as Number).toInt()
                    gameStartCallback?.invoke(idx, numPlayers)
                }
                JsMessageType.PLAYER_INPUT -> {
                    val idx = (data.playerIndex as Number).toInt()
                    val input = PlayerInputData(
                        rotation = (data.rotation as Number).toFloat(),
                        isMoving = data.isMoving as Boolean,
                        isRightDown = data.isRightDown as Boolean,
                        mouseOffsetX = (data.mouseOffsetX as Number).toFloat(),
                        mouseOffsetY = (data.mouseOffsetY as Number).toFloat(),
                        scrollDelta = (data.scrollDelta as? Number)?.toFloat() ?: 0f,
                    )
                    playerInputCallback?.invoke(idx, input)
                }
                JsMessageType.GAME_SYNC -> {
                    val players = (data.players as Array<dynamic>).map { p ->
                        PlayerSyncData(
                            id = (p.id as Number).toInt(),
                            x = (p.x as Number).toFloat(),
                            y = (p.y as Number).toFloat(),
                            rotation = (p.rotation as Number).toFloat(),
                            hp = (p.hp as Number).toFloat(),
                            isAlive = p.isAlive as Boolean,
                            isSpawning = p.isSpawning as Boolean,
                            kills = (p.kills as Number).toInt(),
                            selectedSlotIndex = (p.selectedSlotIndex as Number).toInt(),
                            fireCooldown = (p.fireCooldown as Number).toFloat(),
                            velocityX = (p.velocityX as Number).toFloat(),
                            velocityY = (p.velocityY as Number).toFloat(),
                        )
                    }
                    val projectiles = (data.projectiles as? Array<dynamic>)?.map { proj ->
                        ProjectileSyncData(
                            id = (proj.id as Number).toInt(),
                            ownerId = (proj.ownerId as Number).toInt(),
                            x = (proj.x as Number).toFloat(),
                            y = (proj.y as Number).toFloat(),
                            vx = (proj.vx as Number).toFloat(),
                            vy = (proj.vy as Number).toFloat(),
                            damage = (proj.damage as Number).toFloat(),
                            radius = (proj.radius as Number).toFloat(),
                            color = (proj.color as Number).toLong(),
                            lifeTime = (proj.lifeTime as Number).toFloat(),
                            maxLifeTime = (proj.maxLifeTime as Number).toFloat(),
                            isExplosive = proj.isExplosive as Boolean,
                            explosionRadius = (proj.explosionRadius as Number).toFloat(),
                        )
                    } ?: emptyList()
                    val killFeed = (data.killFeed as? Array<dynamic>)?.map { it.toString() } ?: emptyList()
                    gameSyncCallback?.invoke(GameSyncData(
                        players = players,
                        projectiles = projectiles,
                        gameTime = (data.gameTime as Number).toFloat(),
                        battleZoneRadius = (data.battleZoneRadius as Number).toFloat(),
                        isGameOver = data.isGameOver as Boolean,
                        winnerId = (data.winnerId as Number).toInt(),
                        killFeed = killFeed,
                    ))
                }
                JsMessageType.GAME_OVER -> {
                    val winnerId = (data.winnerId as Number).toInt()
                    gameOverCallback?.invoke(winnerId)
                }
                JsMessageType.SHOOT -> {
                    val idx = (data.playerIndex as Number).toInt()
                    shootCallback?.invoke(idx)
                }
                JsMessageType.PICKUP -> {
                    val idx = (data.playerIndex as Number).toInt()
                    pickupCallback?.invoke(idx)
                }
            }
        }
    }

    override fun hostGame() {
        network.hostGame()
        roomCode = network.roomCode
    }

    override fun joinGame(code: String) {
        network.joinGame(code)
        roomCode = network.roomCode
    }

    override fun disconnect() {
        network.disconnect()
        state = LobbyConnectionState.Idle
        errorMessage = null
        roomCode = ""
    }

    override fun onStateChanged(callback: (LobbyConnectionState) -> Unit) { stateCallback = callback }
    override fun onGameStartReceived(callback: (playerIndex: Int, numPlayers: Int) -> Unit) { gameStartCallback = callback }
    override fun onPlayerInputReceived(callback: (playerIndex: Int, data: PlayerInputData) -> Unit) { playerInputCallback = callback }
    override fun onGameSyncReceived(callback: (GameSyncData) -> Unit) { gameSyncCallback = callback }
    override fun onGameOverReceived(callback: (winnerId: Int) -> Unit) { gameOverCallback = callback }
    override fun onShootReceived(callback: (playerIndex: Int) -> Unit) { shootCallback = callback }
    override fun onPickupReceived(callback: (playerIndex: Int) -> Unit) { pickupCallback = callback }

    override fun sendGameStart(numPlayers: Int) { network.sendGameStart(numPlayers) }
    override fun sendPlayerInput(playerIndex: Int, data: PlayerInputData) { network.sendPlayerInput(playerIndex, data) }
    override fun sendGameSync(data: GameSyncData) { network.sendGameSync(data) }
    override fun sendGameOver(winnerId: Int) { network.sendGameOver(winnerId) }
    override fun sendShoot(playerIndex: Int) { network.sendShoot(playerIndex) }
    override fun sendPickup(playerIndex: Int) { network.sendPickup(playerIndex) }
}
