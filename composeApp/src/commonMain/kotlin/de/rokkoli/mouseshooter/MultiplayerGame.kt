package de.rokkoli.mouseshooter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.atan2

// ---------------------------------------------------------------------------
// Multiplayer Game Screen — Host-authoritative architecture
// ---------------------------------------------------------------------------
// Host runs the full GameEngine simulation.
// Guests send input, receive state sync.

private val MP_PLAYER_COLORS = listOf(
    0xFF00CCFFL,   // P1 Cyan
    0xFFFF4444L,   // P2 Red
    0xFF39FF14L,   // P3 Lime
    0xFFFFFF00L,   // P4 Yellow
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MultiplayerGameScreen(
    connector: MultiplayerConnector,
    isHost: Boolean,
    onBack: () -> Unit,
) {
    var myPlayerIndex by remember { mutableStateOf(if (isHost) 0 else -1) }
    var gameState by remember { mutableStateOf<GameState?>(null) }
    var gameStarted by remember { mutableStateOf(false) }
    var connectionLost by remember { mutableStateOf(false) }
    var gameSeed by remember { mutableStateOf(0) }

    var mousePos    by remember { mutableStateOf(Vec2(400f, 300f)) }
    var isRightDown by remember { mutableStateOf(false) }
    var isLeftDown  by remember { mutableStateOf(false) }
    var wasLeftDown by remember { mutableStateOf(false) }
    var screenSize  by remember { mutableStateOf(Vec2(1280f, 800f)) }

    val focusRequester = remember { FocusRequester() }
    var lastMark by remember { mutableStateOf(kotlin.time.TimeSource.Monotonic.markNow()) }
    var syncCounter by remember { mutableStateOf(0) }
    val syncInterval = 2  // Sync alle 2 Frames

    // ── Initialisierung (Host) ─────────────────────────────────────────────
    LaunchedEffect(Unit) {
        if (isHost) {
            val numPlayers = connector.connectedPlayers
            val seed = kotlin.random.Random.nextInt()
            gameSeed = seed
            gameState = createMultiplayerInitialState(numPlayers, seed)
            connector.sendGameStart(numPlayers, seed)
            gameStarted = true
        }
    }

    // ── Nachricht-Handler ──────────────────────────────────────────────────
    LaunchedEffect(connector) {
        connector.onStateChanged { state ->
            if ((state == LobbyConnectionState.Idle || state == LobbyConnectionState.Error)) {
                connectionLost = true
            }
        }

        connector.onGameStartReceived { playerIndex, numPlayers, seed ->
            if (!isHost) {
                myPlayerIndex = playerIndex
                gameSeed = seed
                // Erstelle lokalen Platzhalter-State bis erster Sync kommt
                gameState = createMultiplayerInitialState(numPlayers, seed)
                gameStarted = true
            }
        }

        connector.onPlayerInputReceived { playerIndex, inputData ->
            if (isHost) {
                val gs = gameState ?: return@onPlayerInputReceived
                // Setze Input des Remote-Players
                gameState = gs.copy(players = gs.players.map { p ->
                    if (p.id == playerIndex) {
                        p.copy(
                            rotation = inputData.rotation,
                            isMovingIntent = inputData.isMoving
                        )
                    } else p
                })

                // Waffen-Wechsel
                if (inputData.scrollDelta != 0f) {
                    gameState = GameEngine.scrollSlot(gameState!!, playerIndex, inputData.scrollDelta > 0f)
                }
            }
        }

        connector.onShootReceived { playerIndex ->
            if (isHost) {
                val gs = gameState ?: return@onShootReceived
                gameState = GameEngine.shoot(gs, playerIndex)
            }
        }

        connector.onPickupReceived { playerIndex ->
            if (isHost) {
                val gs = gameState ?: return@onPickupReceived
                gameState = GameEngine.pickupNearby(gs, playerIndex)
            }
        }

        connector.onGameSyncReceived { syncData ->
            if (!isHost) {
                gameState = applyGameSync(gameState, syncData, gameSeed)
            }
        }

        connector.onGameOverReceived { winnerId ->
            val gs = gameState ?: return@onGameOverReceived
            gameState = gs.copy(isGameOver = true, winnerId = winnerId)
        }
    }

    // ── Game-Loop ──────────────────────────────────────────────────────────
    LaunchedEffect(gameStarted) {
        if (!gameStarted) return@LaunchedEffect
        focusRequester.requestFocus()

        while (isActive) {
            val dt = (lastMark.elapsedNow().inWholeMilliseconds / 1000f).toFloat().coerceAtMost(0.05f)
            lastMark = kotlin.time.TimeSource.Monotonic.markNow()

            val gs = gameState
            if (gs != null && !gs.isGameOver && !connectionLost) {

                if (isHost) {
                    // ── HOST: Volle Simulation ────────────────────────────
                    val zoom = gs.zoomLevel
                    val screenCenter = Vec2(screenSize.x / 2, screenSize.y / 2)

                    // Lokaler Host-Spieler Input (Markiere P0 als Lokal für Engine!)
                    val localPlayer = gs.players.firstOrNull { it.id == 0 && it.isAlive }
                    if (localPlayer != null && !localPlayer.isSpawning) {
                        val toMouse = (mousePos - screenCenter) * (1f / zoom)
                        val rotation = atan2(toMouse.y, toMouse.x)
                        gameState = gs.copy(players = gs.players.map {
                            if (it.id == 0 && it.isAlive) {
                                it.copy(rotation = rotation, isLocalPlayer = true, isMovingIntent = !isRightDown)
                            } else {
                                // WICHTIG: Host muss alle anderen Spieler als Remote lassen
                                it.copy(isLocalPlayer = false)
                            }
                        })
                    }

                    // Schießen (Host Player)
                    if (isLeftDown) {
                        val p = gameState!!.players.firstOrNull { it.id == 0 && it.isAlive }
                        val w = p?.inventory?.activeWeapon
                        val isAuto = w == WeaponType.SMG || w == WeaponType.FLAMETHROWER || w == WeaponType.MINIGUN
                        if (isAuto || !wasLeftDown) {
                            if (p != null) gameState = GameEngine.shoot(gameState!!, 0)
                        }
                    }
                    wasLeftDown = isLeftDown

                    // Remote-Spieler Bewegung basierend auf Intent
                    var updatedState = gameState!!
                    updatedState = updatedState.copy(players = updatedState.players.map { p ->
                        if (p.id == 0 || !p.isAlive || p.isSpawning) return@map p
                        if (!p.isMovingIntent) return@map p.copy(velocity = Vec2(0f, 0f))

                        val dir = Vec2(kotlin.math.cos(p.rotation), kotlin.math.sin(p.rotation))
                        val vel = dir * PLAYER_SPEED
                        var newPos = p.pos + vel * dt
                        newPos = GameEngine.resolveObstacleCollision(newPos, updatedState.obstacles, PLAYER_RADIUS)
                        newPos = newPos.clampToMap(updatedState.mapWidth, updatedState.mapHeight)
                        p.copy(pos = newPos, velocity = vel)
                    })

                    // Update (für Host-Spieler Bewegung + Physics)
                    gameState = GameEngine.update(updatedState, dt, mousePos, isRightDown, screenSize)

                    // State Sync
                    syncCounter++
                    if (syncCounter >= syncInterval) {
                        syncCounter = 0
                        connector.sendGameSync(createGameSyncData(gameState!!))
                    }

                    // Game Over
                    if (gameState!!.isGameOver) {
                        connector.sendGameOver(gameState!!.winnerId)
                    }

                } else {
                    // ── GUEST: Sende Input, empfange Sync ─────────────────
                    val zoom = gs.zoomLevel
                    val screenCenter = Vec2(screenSize.x / 2, screenSize.y / 2)
                    val toMouse = (mousePos - screenCenter) * (1f / zoom)
                    val rotation = atan2(toMouse.y, toMouse.x)

                    connector.sendPlayerInput(myPlayerIndex, PlayerInputData(
                        rotation = rotation,
                        isMoving = !isRightDown,
                        isRightDown = isRightDown,
                        mouseOffsetX = toMouse.x,
                        mouseOffsetY = toMouse.y,
                    ))

                    // Schießen
                    if (isLeftDown) {
                        val p = gs.players.firstOrNull { it.id == myPlayerIndex && it.isAlive }
                        val w = p?.inventory?.activeWeapon
                        val isAuto = w == WeaponType.SMG || w == WeaponType.FLAMETHROWER || w == WeaponType.MINIGUN
                        if (isAuto || !wasLeftDown) {
                            connector.sendShoot(myPlayerIndex)
                        }
                    }
                    wasLeftDown = isLeftDown

                    // Guest: Lokale Simulation (Prediction) für flüssiges Laufen
                    gameState = gs.copy(players = gs.players.map { p ->
                        if (p.id == myPlayerIndex && p.isAlive && !p.isSpawning) {
                            var pCopy = p.copy(rotation = rotation)
                            if (!isRightDown) {
                                val dir = Vec2(kotlin.math.cos(rotation), kotlin.math.sin(rotation))
                                val vel = dir * PLAYER_SPEED
                                var newPos = pCopy.pos + vel * dt
                                newPos = GameEngine.resolveObstacleCollision(newPos, gs.obstacles, PLAYER_RADIUS)
                                newPos = newPos.clampToMap(gs.mapWidth, gs.mapHeight)
                                pCopy = pCopy.copy(pos = newPos, velocity = vel)
                            } else {
                                pCopy = pCopy.copy(velocity = Vec2(0f, 0f))
                            }
                            pCopy
                        } else p
                    })

                    // Kamera auf Predicted Position
                    val localP = gameState!!.players.firstOrNull { it.id == myPlayerIndex }
                    if (localP != null) {
                        gameState = gameState!!.copy(cameraX = localP.pos.x, cameraY = localP.pos.y)
                    }
                }
            }
            delay(16L)
        }
    }

    // ── Rendering ──────────────────────────────────────────────────────────
    val gs = gameState
    val localPlayer = gs?.players?.firstOrNull { it.id == myPlayerIndex }
    val isBlind = localPlayer?.statusEffects?.blindTimer?.let { it > 0f } ?: false

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            // ── Keyboard-Events ──────────────────────────────────────────
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (keyEvent.key == Key.E) {
                        if (isHost) {
                            gameState = gameState?.let { GameEngine.pickupNearby(it, myPlayerIndex) }
                        } else {
                            connector.sendPickup(myPlayerIndex)
                        }
                        return@onKeyEvent true
                    }
                }
                false
            }
            // ── Maus-Tracking ─────────────────────────────────────────────
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event  = awaitPointerEvent(PointerEventPass.Main)
                        val pos    = event.changes.firstOrNull()?.position ?: continue
                        mousePos   = Vec2(pos.x, pos.y)
                        isLeftDown  = event.buttons.isPrimaryPressed
                        isRightDown = event.buttons.isSecondaryPressed

                        // Mausrad-Klick → Item aufheben
                        if (event.buttons.isTertiaryPressed) {
                            if (isHost) {
                                gameState = gameState?.let { GameEngine.pickupNearby(it, myPlayerIndex) }
                            } else {
                                connector.sendPickup(myPlayerIndex)
                            }
                        }

                        for (change in event.changes) change.consume()
                    }
                }
            }
            // ── Scroll-Rad ────────────────────────────────────────────────
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val delta = event.changes.firstOrNull()?.scrollDelta ?: continue
                        if (delta.y != 0f) {
                            if (isHost) {
                                gameState = gameState?.let { GameEngine.scrollSlot(it, myPlayerIndex, delta.y < 0) }
                            } else {
                                connector.sendPlayerInput(myPlayerIndex, PlayerInputData(
                                    rotation = localPlayer?.rotation ?: 0f,
                                    isMoving = true,
                                    isRightDown = false,
                                    mouseOffsetX = 0f,
                                    mouseOffsetY = 0f,
                                    scrollDelta = delta.y,
                                ))
                            }
                        }
                    }
                }
            }
    ) {
        if (gs != null && gameStarted) {
            // ── Canvas-Rendering ──────────────────────────────────────────
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sw = size.width
                val sh = size.height
                screenSize = Vec2(sw, sh)

                // Markiere den richtigen Spieler als "lokal" für die Kamera
                val adjustedState = gs.copy(
                    players = gs.players.map { p ->
                        p.copy(isLocalPlayer = p.id == myPlayerIndex)
                    }
                )

                with(GameRenderer) {
                    renderGame(adjustedState, sw, sh, isBlind)
                    val minimapSize = 140f
                    renderMinimap(adjustedState, sw - minimapSize - 12f, sh - minimapSize - 12f, minimapSize)
                }

                // Fadenkreuz
                if (!isBlind) {
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(sw / 2 - 10, sh / 2), Offset(sw / 2 + 10, sh / 2), 1.5f)
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(sw / 2, sh / 2 - 10), Offset(sw / 2, sh / 2 + 10), 1.5f)
                    drawCircle(Color.White.copy(alpha = 0.2f), 4f, center = Offset(sw / 2, sh / 2))
                }
            }

            // ── HUD ───────────────────────────────────────────────────────
            if (!gs.isGameOver) {
                GameHUD(
                    state = gs.copy(players = gs.players.map { p ->
                        p.copy(isLocalPlayer = p.id == myPlayerIndex)
                    }),
                    localPlayer = localPlayer?.copy(isLocalPlayer = true),
                    onArmorClick = {
                        val player = gs.players.firstOrNull { it.id == myPlayerIndex && it.isAlive }
                        if (player != null && isHost) {
                            gameState = when (player.inventory.armorSlot) {
                                ArmorType.AGILITY -> GameEngine.dash(gs, player.id)
                                ArmorType.STEALTH -> GameEngine.activateStealth(gs, player.id)
                                else -> gs
                            }
                        }
                    }
                )

                val spawningPlayer = localPlayer
                if (spawningPlayer?.isSpawning == true) {
                    SpawnCountdown(spawningPlayer.spawnTimer)
                }

                // Multiplayer-Indikator
                Box(
                    modifier = Modifier.padding(16.dp)
                        .background(Color(0x88000000), RoundedCornerShape(8.dp)).padding(12.dp)
                ) {
                    Column {
                        Text(
                            "MULTIPLAYER • ${connector.connectedPlayers} Spieler",
                            color = Color(0xFF00CCFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Raum: ${connector.roomCode} • Du bist P${myPlayerIndex + 1}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            // ── Connection Lost ────────────────────────────────────────────
            if (connectionLost) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("VERBINDUNG VERLOREN", color = Color(0xFFFFCC00), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0xFFAAAAAA), RoundedCornerShape(4.dp))
                                .clickable { onBack() }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("ZURÜCK ZUM MENÜ", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFAAAAAA))
                        }
                    }
                }
            }

            // ── Game Over ──────────────────────────────────────────────────
            if (gs.isGameOver) {
                GameOverScreen(
                    state = gs.copy(players = gs.players.map { p ->
                        p.copy(isLocalPlayer = p.id == myPlayerIndex)
                    }),
                    onRestart = {
                        connector.disconnect()
                        onBack()
                    }
                )
            }
        } else {
            // Warte auf Spielstart
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0A1020)),
                contentAlignment = Alignment.Center,
            ) {
                Text("WARTE AUF SPIELSTART...", color = Color(0xFF00CCFF), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    LaunchedEffect(Unit) { delay(100); focusRequester.requestFocus() }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Erzeugt den Multiplayer-Startzustand: Alle Spieler sind echte Spieler, keine Bots. */
fun createMultiplayerInitialState(numPlayers: Int, seed: Int): GameState {
    val mapW = 5500f
    val mapH = 5500f
    val center = Vec2(mapW / 2, mapH / 2)
    val (obstacles, items) = MapGenerator.generate(mapW, mapH, kotlin.random.Random(seed))

    val players = mutableListOf<Player>()

    for (i in 0 until numPlayers) {
        val spreadAngle = (i.toFloat() / numPlayers) * 2 * kotlin.math.PI.toFloat()
        val spawnDist = 60f + i * 20f
        val pos = Vec2(center.x + kotlin.math.cos(spreadAngle) * spawnDist, center.y + kotlin.math.sin(spreadAngle) * spawnDist)
        players.add(Player(
            id = i,
            pos = pos,
            hp = 100f,
            isLocalPlayer = false,  // wird zur Renderzeit überschrieben
            inventory = Inventory(meleeSlot = WeaponType.FISTS, gunSlots = listOf(null, null, null), selectedSlotIndex = 0),
            color = MP_PLAYER_COLORS[i % MP_PLAYER_COLORS.size],
            isSpawning = true,
            spawnTimer = 3.5f,
            spreadAngle = spreadAngle,
            wanderAngle = spreadAngle,
        ))
    }

    return GameState(
        players = players, groundItems = items, obstacles = obstacles,
        battleZone = BattleZone(3000f, 150f, center.x, center.y, shrinkRate = 4f),
        mapWidth = mapW, mapHeight = mapH,
        cameraX = center.x, cameraY = center.y,
        nextId = 2000, zoomLevel = 1.8f,
    )
}

/** Erzeugt GameSyncData aus dem aktuellen GameState. */
fun createGameSyncData(state: GameState): GameSyncData {
    return GameSyncData(
        players = state.players.map { p ->
            PlayerSyncData(
                id = p.id,
                x = p.pos.x,
                y = p.pos.y,
                rotation = p.rotation,
                hp = p.hp,
                isAlive = p.isAlive,
                isSpawning = p.isSpawning,
                kills = p.kills,
                selectedSlotIndex = p.inventory.selectedSlotIndex,
                fireCooldown = p.fireCooldown,
                velocityX = p.velocity.x,
                velocityY = p.velocity.y,
            )
        },
        projectiles = state.projectiles.map { proj ->
            ProjectileSyncData(
                id = proj.id,
                ownerId = proj.ownerId,
                x = proj.pos.x,
                y = proj.pos.y,
                vx = proj.velocity.x,
                vy = proj.velocity.y,
                damage = proj.damage,
                radius = proj.radius,
                color = proj.color,
                lifeTime = proj.lifeTime,
                maxLifeTime = proj.maxLifeTime,
                isExplosive = proj.isExplosive,
                explosionRadius = proj.explosionRadius,
            )
        },
        meleeSwings = state.meleeSwings.map { ms ->
            MeleeSwingSyncData(
                ownerId = ms.ownerId,
                x = ms.pos.x,
                y = ms.pos.y,
                dirX = ms.direction.x,
                dirY = ms.direction.y,
                range = ms.range,
                weaponLabel = ms.weapon.label,
                isLeft = ms.isLeft
            )
        },
        explosions = state.explosions.map { exp ->
            ExplosionSyncData(
                x = exp.pos.x,
                y = exp.pos.y,
                currentRadius = exp.currentRadius,
                maxRadius = exp.maxRadius
            )
        },
        grenades = state.grenades.map { g ->
            GrenadeSyncData(
                id = g.id,
                ownerId = g.ownerId,
                pos = g.pos,
                color = g.grenadeType.color
            )
        },
        groundItems = state.groundItems.map { gi ->
            val type = when(gi) {
                is GroundItem.WeaponItem -> 0
                is GroundItem.GrenadeItem -> 1
                is GroundItem.ArmorItem -> 2
            }
            val label = when(gi) {
                is GroundItem.WeaponItem -> gi.weaponType.name
                is GroundItem.GrenadeItem -> gi.grenadeType.name
                is GroundItem.ArmorItem -> gi.armorType.name
            }
            GroundItemSyncData(gi.id, type, gi.pos.x, gi.pos.y, label, gi.rarity.ordinal)
        },
        effectZones = state.effectZones.map { ez ->
            EffectZoneSyncData(ez.id, ez.pos.x, ez.pos.y, ez.radius, ez.type.ordinal, ez.color)
        },
        gameTime = state.gameTime,
        battleZoneRadius = state.battleZone.currentRadius,
        isGameOver = state.isGameOver,
        winnerId = state.winnerId,
        killFeed = state.killFeed,
    )
}

/** Wendet GameSyncData auf den lokalen State an. */
fun applyGameSync(currentState: GameState?, syncData: GameSyncData, seed: Int): GameState {
    val base = currentState ?: createMultiplayerInitialState(syncData.players.size, seed)

    val updatedPlayers = syncData.players.map { sp ->
        val existing = base.players.firstOrNull { it.id == sp.id }
        (existing ?: Player(id = sp.id, pos = Vec2(sp.x, sp.y))).copy(
            pos = Vec2(sp.x, sp.y),
            rotation = sp.rotation,
            hp = sp.hp,
            isAlive = sp.isAlive,
            isSpawning = sp.isSpawning,
            kills = sp.kills,
            inventory = (existing?.inventory ?: Inventory()).copy(selectedSlotIndex = sp.selectedSlotIndex),
            fireCooldown = sp.fireCooldown,
            velocity = Vec2(sp.velocityX, sp.velocityY),
        )
    }

    val updatedProjectiles = syncData.projectiles.map { pp ->
        Projectile(
            id = pp.id,
            ownerId = pp.ownerId,
            pos = Vec2(pp.x, pp.y),
            velocity = Vec2(pp.vx, pp.vy),
            damage = pp.damage,
            radius = pp.radius,
            color = pp.color,
            lifeTime = pp.lifeTime,
            maxLifeTime = pp.maxLifeTime,
            isExplosive = pp.isExplosive,
            explosionRadius = pp.explosionRadius,
        )
    }

    val updatedMeleeSwings = syncData.meleeSwings.map { ms ->
        val weapon = WeaponType.entries.firstOrNull { it.label == ms.weaponLabel } ?: WeaponType.FISTS
        MeleeSwing(
            ownerId = ms.ownerId,
            weapon = weapon,
            isLeft = ms.isLeft,
            pos = Vec2(ms.x, ms.y),
            direction = Vec2(ms.dirX, ms.dirY),
            range = ms.range,
            damage = 0f, // Nur für Anzeige, Host berechnet Schaden
            knockback = 0f,
        )
    }

    val updatedExplosions = syncData.explosions.map { exp ->
        Explosion(
            pos = Vec2(exp.x, exp.y),
            maxRadius = exp.maxRadius,
            currentRadius = exp.currentRadius,
            timer = 0f, // Wird lokal nicht getickt
            duration = 0.4f,
            damage = 0f,
        )
    }

    val updatedGrenades = syncData.grenades.map { gs ->
        ThrownGrenade(
            id = gs.id,
            ownerId = gs.ownerId,
            grenadeType = GrenadeType.entries.firstOrNull { it.color == gs.color } ?: GrenadeType.NORMAL,
            pos = gs.pos,
            velocity = Vec2(0f, 0f),
            timer = 0f,
        )
    }

    val updatedGroundItems = syncData.groundItems.map { gi ->
        val rarity = Rarity.entries.getOrNull(gi.rarity) ?: Rarity.COMMON
        when(gi.type) {
            0 -> GroundItem.WeaponItem(gi.id, Vec2(gi.x, gi.y), WeaponType.valueOf(gi.itemType), rarity)
            1 -> GroundItem.GrenadeItem(gi.id, Vec2(gi.x, gi.y), GrenadeType.valueOf(gi.itemType), rarity)
            else -> GroundItem.ArmorItem(gi.id, Vec2(gi.x, gi.y), ArmorType.valueOf(gi.itemType), rarity)
        }
    }

    val updatedEffectZones = syncData.effectZones.map { ez ->
        EffectZone(
            id = ez.id,
            pos = Vec2(ez.x, ez.y),
            radius = ez.radius,
            type = ZoneType.entries.getOrNull(ez.type) ?: ZoneType.SMOKE,
            timer = 0f,
            duration = 10f,
            color = ez.color
        )
    }

    return base.copy(
        players = updatedPlayers,
        projectiles = updatedProjectiles,
        meleeSwings = updatedMeleeSwings,
        explosions = updatedExplosions,
        grenades = updatedGrenades,
        groundItems = updatedGroundItems,
        effectZones = updatedEffectZones,
        gameTime = syncData.gameTime,
        battleZone = base.battleZone.copy(currentRadius = syncData.battleZoneRadius),
        isGameOver = syncData.isGameOver,
        winnerId = syncData.winnerId,
        killFeed = syncData.killFeed,
    )
}
