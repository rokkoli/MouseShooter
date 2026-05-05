package de.rokkoli.mouseshooter

// ---------------------------------------------------------------------------
// Multiplayer connection abstraction (expect/actual)
// ---------------------------------------------------------------------------
// The lobby UI lives in commonMain but needs to drive the PeerJS connection
// (which is JS-only). This abstract class lets commonMain code drive the
// connection without depending on JS-specific types.
//
// On JS: implemented by wrapping NetworkManager (PeerJS).
// On JVM: stub implementation (multiplayer is unsupported on desktop).

/** Represents the state of a multiplayer connection. */
enum class LobbyConnectionState {
    Idle,
    WaitingForGuest,
    Connecting,
    Connected,
    Error,
}

/** Platform-specific multiplayer connector created via [createMultiplayerConnector]. */
abstract class MultiplayerConnector {

    abstract val state: LobbyConnectionState
    abstract val errorMessage: String?
    abstract val roomCode: String
    abstract val connectedPlayers: Int

    /** Host a new game — generates a room code. */
    abstract fun hostGame()

    /** Join an existing game by room code. */
    abstract fun joinGame(code: String)

    /** Tear down the connection. */
    abstract fun disconnect()

    /** Register a callback for state changes. */
    abstract fun onStateChanged(callback: (LobbyConnectionState) -> Unit)

    // -----------------------------------------------------------------------
    // Game message callbacks (set by the game composable)
    // -----------------------------------------------------------------------

    abstract fun onGameStartReceived(callback: (playerIndex: Int, numPlayers: Int, seed: Int) -> Unit)
    abstract fun onPlayerInputReceived(callback: (playerIndex: Int, data: PlayerInputData) -> Unit)
    abstract fun onGameSyncReceived(callback: (GameSyncData) -> Unit)
    abstract fun onGameOverReceived(callback: (winnerId: Int) -> Unit)
    abstract fun onShootReceived(callback: (playerIndex: Int) -> Unit)
    abstract fun onPickupReceived(callback: (playerIndex: Int) -> Unit)

    // -----------------------------------------------------------------------
    // Send typed messages
    // -----------------------------------------------------------------------

    abstract fun sendGameStart(numPlayers: Int, seed: Int)
    abstract fun sendPlayerInput(playerIndex: Int, data: PlayerInputData)
    abstract fun sendGameSync(data: GameSyncData)
    abstract fun sendGameOver(winnerId: Int)
    abstract fun sendShoot(playerIndex: Int)
    abstract fun sendPickup(playerIndex: Int)
}

/** Per-player input data sent from guest to host. */
data class PlayerInputData(
    val rotation: Float,
    val isMoving: Boolean,
    val isRightDown: Boolean,
    val mouseOffsetX: Float,
    val mouseOffsetY: Float,
    val scrollDelta: Float = 0f,       // Waffen-Wechsel
)

/** Per-player state for network sync (host → guests). */
data class PlayerSyncData(
    val id: Int,
    val x: Float,
    val y: Float,
    val rotation: Float,
    val hp: Float,
    val isAlive: Boolean,
    val isSpawning: Boolean,
    val kills: Int,
    val selectedSlotIndex: Int,
    val fireCooldown: Float,
    val velocityX: Float,
    val velocityY: Float,
    // Inventory
    val meleeSlot: String?,
    val gunSlots: List<String?>,
    val grenadeSlots: List<String?>,
    val armorSlot: String?,
    val clipAmmo: List<Int>,
    val reserveAmmo: Map<String, Int>,
    val meleeRarity: Int,
    val gunRarities: List<Int>,
    val grenadeRarities: List<Int>,
    val armorRarity: Int?,
    val isReloading: Boolean = false,
    val reloadTimer: Float = 0f,
    val spawnTimer: Float = 0f,
)

/** Projectile sync data. */
data class ProjectileSyncData(
    val id: Int,
    val ownerId: Int,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val damage: Float,
    val radius: Float,
    val color: Long,
    val lifeTime: Float,
    val maxLifeTime: Float,
    val isExplosive: Boolean,
    val explosionRadius: Float,
)

/** Grenade sync data. */
data class GrenadeSyncData(
    val id: Int,
    val ownerId: Int,
    val pos: Vec2,
    val color: Long,
)

/** Ground item sync data. */
data class GroundItemSyncData(
    val id: Int,
    val type: Int, // 0=Weapon, 1=Grenade, 2=Armor
    val x: Float,
    val y: Float,
    val itemType: String, // Label or name
    val rarity: Int,
)

/** Effect zone sync data. */
data class EffectZoneSyncData(
    val id: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val type: Int, // ZoneType ordinal
    val color: Long,
)

/** Melee swing sync data. */
data class MeleeSwingSyncData(
    val ownerId: Int,
    val x: Float,
    val y: Float,
    val dirX: Float,
    val dirY: Float,
    val range: Float,
    val weaponLabel: String,
    val isLeft: Boolean,
)

/** Explosion sync data. */
data class ExplosionSyncData(
    val x: Float,
    val y: Float,
    val currentRadius: Float,
    val maxRadius: Float,
)

/** Flattened game state for network sync (host → guests). */
data class GameSyncData(
    val players: List<PlayerSyncData>,
    val projectiles: List<ProjectileSyncData> = emptyList(),
    val meleeSwings: List<MeleeSwingSyncData> = emptyList(),
    val explosions: List<ExplosionSyncData> = emptyList(),
    val grenades: List<GrenadeSyncData> = emptyList(),
    val groundItems: List<GroundItemSyncData> = emptyList(),
    val effectZones: List<EffectZoneSyncData> = emptyList(),
    val gameTime: Float = 0f,
    val battleZoneRadius: Float = 4000f,
    val isGameOver: Boolean = false,
    val winnerId: Int = -1,
    val killFeed: List<String> = emptyList(),
)

/** Factory function — platform-specific. */
expect fun createMultiplayerConnector(): MultiplayerConnector
