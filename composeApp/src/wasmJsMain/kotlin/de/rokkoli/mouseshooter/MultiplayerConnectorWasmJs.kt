@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package de.rokkoli.mouseshooter

// ---------------------------------------------------------------------------
// WasmJS implementation of MultiplayerConnector
// ---------------------------------------------------------------------------

actual fun createMultiplayerConnector(): MultiplayerConnector = WasmJsMultiplayerConnector()

class WasmJsMultiplayerConnector : MultiplayerConnector() {

    private val network = WasmJsNetworkManager()

    override var state: LobbyConnectionState = LobbyConnectionState.Idle
        private set

    override var errorMessage: String? = null
        private set

    override var roomCode: String = ""
        private set

    override val connectedPlayers: Int
        get() = if (network.isGuest) 2 else network.numConnections + 1

    private var stateCallback: ((LobbyConnectionState) -> Unit)? = null
    private var gameStartCallback: ((Int, Int, Int) -> Unit)? = null
    private var playerInputCallback: ((Int, PlayerInputData) -> Unit)? = null
    private var gameSyncCallback: ((GameSyncData) -> Unit)? = null
    private var gameOverCallback: ((Int) -> Unit)? = null
    private var shootCallback: ((Int) -> Unit)? = null
    private var pickupCallback: ((Int) -> Unit)? = null

    init {
        network.onStateChanged = { connState ->
            state = when (connState) {
                WasmConnectionState.Idle -> LobbyConnectionState.Idle
                WasmConnectionState.WaitingForGuest -> LobbyConnectionState.WaitingForGuest
                WasmConnectionState.Connecting -> LobbyConnectionState.Connecting
                WasmConnectionState.Connected -> LobbyConnectionState.Connected
                WasmConnectionState.Error -> LobbyConnectionState.Error
            }
            errorMessage = network.errorMessage
            stateCallback?.invoke(state)
        }

        network.onMessageReceived = { type, dataAny ->
            when (type) {
                WasmMessageType.GAME_START -> {
                    val idx = getJsInt(dataAny, "playerIndex")
                    val numPlayers = getJsInt(dataAny, "numPlayers")
                    val seed = getJsInt(dataAny, "seed")
                    gameStartCallback?.invoke(idx, numPlayers, seed)
                }
                WasmMessageType.PLAYER_INPUT -> {
                    val idx = getJsInt(dataAny, "playerIndex")
                    val input = PlayerInputData(
                        rotation = getJsFloat(dataAny, "rotation"),
                        isMoving = getJsBoolean(dataAny, "isMoving"),
                        isRightDown = getJsBoolean(dataAny, "isRightDown"),
                        mouseOffsetX = getJsFloat(dataAny, "mouseOffsetX"),
                        mouseOffsetY = getJsFloat(dataAny, "mouseOffsetY"),
                        scrollDelta = getJsFloat(dataAny, "scrollDelta"),
                    )
                    playerInputCallback?.invoke(idx, input)
                }
                WasmMessageType.GAME_SYNC -> {
                    val playersArr = getJsArray(dataAny, "players")
                    val playersLen = getJsArrayLength(playersArr)
                    val players = mutableListOf<PlayerSyncData>()
                    for (i in 0 until playersLen) {
                        val pObj = getJsArrayItem(playersArr, i)
                        players.add(PlayerSyncData(
                            id = getJsInt(pObj, "id"),
                            x = getJsFloat(pObj, "x"),
                            y = getJsFloat(pObj, "y"),
                            rotation = getJsFloat(pObj, "rotation"),
                            hp = getJsFloat(pObj, "hp"),
                            isAlive = getJsBoolean(pObj, "isAlive"),
                            isSpawning = getJsBoolean(pObj, "isSpawning"),
                            kills = getJsInt(pObj, "kills"),
                            selectedSlotIndex = getJsInt(pObj, "selectedSlotIndex"),
                            fireCooldown = getJsFloat(pObj, "fireCooldown"),
                            velocityX = getJsFloat(pObj, "velocityX"),
                            velocityY = getJsFloat(pObj, "velocityY"),
                        ))
                    }
                    val projArr = getJsArray(dataAny, "projectiles")
                    val projLen = getJsArrayLength(projArr)
                    val projectiles = mutableListOf<ProjectileSyncData>()
                    for (i in 0 until projLen) {
                        val pObj = getJsArrayItem(projArr, i)
                        projectiles.add(ProjectileSyncData(
                            id = getJsInt(pObj, "id"),
                            ownerId = getJsInt(pObj, "ownerId"),
                            x = getJsFloat(pObj, "x"),
                            y = getJsFloat(pObj, "y"),
                            vx = getJsFloat(pObj, "vx"),
                            vy = getJsFloat(pObj, "vy"),
                            damage = getJsFloat(pObj, "damage"),
                            radius = getJsFloat(pObj, "radius"),
                            color = getJsLong(pObj, "color").toLong(),
                            lifeTime = getJsFloat(pObj, "lifeTime"),
                            maxLifeTime = getJsFloat(pObj, "maxLifeTime"),
                            isExplosive = getJsBoolean(pObj, "isExplosive"),
                            explosionRadius = getJsFloat(pObj, "explosionRadius"),
                        ))
                    }
                    val killFeedArr = getJsArray(dataAny, "killFeed")
                    val killFeedLen = getJsArrayLength(killFeedArr)
                    val killFeed = mutableListOf<String>()
                    for (i in 0 until killFeedLen) {
                        val item = getJsArrayItem(killFeedArr, i)
                        val text = getJsString(item, "text") ?: ""
                        killFeed.add(text)
                    }

                    gameSyncCallback?.invoke(GameSyncData(
                        players = players,
                        projectiles = projectiles,
                        gameTime = getJsFloat(dataAny, "gameTime"),
                        battleZoneRadius = getJsFloat(dataAny, "battleZoneRadius"),
                        isGameOver = getJsBoolean(dataAny, "isGameOver"),
                        winnerId = getJsInt(dataAny, "winnerId"),
                        killFeed = killFeed,
                    ))
                }
                WasmMessageType.GAME_OVER -> {
                    val winnerId = getJsInt(dataAny, "winnerId")
                    gameOverCallback?.invoke(winnerId)
                }
                WasmMessageType.SHOOT -> {
                    val idx = getJsInt(dataAny, "playerIndex")
                    shootCallback?.invoke(idx)
                }
                WasmMessageType.PICKUP -> {
                    val idx = getJsInt(dataAny, "playerIndex")
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
    override fun onGameStartReceived(callback: (playerIndex: Int, numPlayers: Int, seed: Int) -> Unit) { gameStartCallback = callback }
    override fun onPlayerInputReceived(callback: (playerIndex: Int, data: PlayerInputData) -> Unit) { playerInputCallback = callback }
    override fun onGameSyncReceived(callback: (GameSyncData) -> Unit) { gameSyncCallback = callback }
    override fun onGameOverReceived(callback: (winnerId: Int) -> Unit) { gameOverCallback = callback }
    override fun onShootReceived(callback: (playerIndex: Int) -> Unit) { shootCallback = callback }
    override fun onPickupReceived(callback: (playerIndex: Int) -> Unit) { pickupCallback = callback }

    override fun sendGameStart(numPlayers: Int, seed: Int) { network.sendGameStart(numPlayers, seed) }
    override fun sendPlayerInput(playerIndex: Int, data: PlayerInputData) { network.sendPlayerInput(playerIndex, data) }
    override fun sendGameSync(data: GameSyncData) {
        network.sendGameSync(data.players, data.projectiles, data.gameTime, data.battleZoneRadius, data.isGameOver, data.winnerId, data.killFeed)
    }
    override fun sendGameOver(winnerId: Int) { network.sendGameOver(winnerId) }
    override fun sendShoot(playerIndex: Int) { network.sendShoot(playerIndex) }
    override fun sendPickup(playerIndex: Int) { network.sendPickup(playerIndex) }
}
