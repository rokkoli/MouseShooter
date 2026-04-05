package de.rokkoli.mouseshooter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.atan2

// ─── Spielbildschirme ─────────────────────────────────────────────────────────
enum class Screen { MENU, GAME }
enum class GameMode { SINGLEPLAYER, MULTIPLAYER }

// ─── Haupt-App ────────────────────────────────────────────────────────────────
@Composable
fun App() {
    var screen by remember { mutableStateOf(Screen.MENU) }
    var gameMode by remember { mutableStateOf(GameMode.SINGLEPLAYER) }

    when (screen) {
        Screen.MENU -> MainMenu(
            onStartSingleplayer = { gameMode = GameMode.SINGLEPLAYER; screen = Screen.GAME },
            onStartMultiplayer = { gameMode = GameMode.MULTIPLAYER; screen = Screen.GAME }
        )
        Screen.GAME -> GameScreen(mode = gameMode, onRestart = { screen = Screen.MENU })
    }
}

// ─── Spielbildschirm ──────────────────────────────────────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameScreen(mode: GameMode, onRestart: () -> Unit) {
    var gameState   by remember { mutableStateOf(createInitialState()) }
    var mousePos    by remember { mutableStateOf(Vec2(400f, 300f)) }
    var isRightDown by remember { mutableStateOf(false) }
    var isLeftDown  by remember { mutableStateOf(false) }
    var wasLeftDown by remember { mutableStateOf(false) }
    var screenSize  by remember { mutableStateOf(Vec2(1280f, 800f)) }

    val focusRequester = remember { FocusRequester() }
    var lastMark by remember { mutableStateOf(kotlin.time.TimeSource.Monotonic.markNow()) }

    // ── Game-Loop ─────────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        while (isActive) {
            val dt  = (lastMark.elapsedNow().inWholeMilliseconds / 1000f).toFloat().coerceAtMost(0.05f)
            lastMark = kotlin.time.TimeSource.Monotonic.markNow()

            if (!gameState.isGameOver) {
                val zoom = gameState.zoomLevel
                val screenCenter = Vec2(screenSize.x / 2, screenSize.y / 2)

                // Mausrichtung (zoom-korrigiert) für lokalen Spieler setzen
                val localPlayer = gameState.players.firstOrNull { it.isLocalPlayer && it.isAlive }
                if (localPlayer != null && !localPlayer.isSpawning) {
                    val toMouse = (mousePos - screenCenter) * (1f / zoom)
                    val rotation = atan2(toMouse.y, toMouse.x)
                    gameState = gameState.copy(players = gameState.players.map {
                        if (it.isLocalPlayer && it.isAlive) it.copy(rotation = rotation) else it
                    })
                }

                // Schießen (Semi-Auto außer bei SMG/Flammenwerfer)
                if (isLeftDown) {
                    val w = localPlayer?.inventory?.activeWeapon
                    val isAuto = w == WeaponType.SMG || w == WeaponType.FLAMETHROWER
                    if (isAuto || !wasLeftDown) {
                        val localId = localPlayer?.id ?: -1
                        if (localId >= 0) {
                            gameState = GameEngine.shoot(gameState, localId)
                        }
                    }
                }
                wasLeftDown = isLeftDown

                // Update
                gameState = GameEngine.update(gameState, dt, mousePos, isRightDown, screenSize)
            }
            delay(16L)  // ~60 FPS
        }
    }

    val localPlayer   = gameState.players.firstOrNull { it.isLocalPlayer }
    val isBlind       = localPlayer?.statusEffects?.blindTimer?.let { it > 0f } ?: false

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()

            // ── Maus-Tracking ─────────────────────────────────────────────────
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event  = awaitPointerEvent(PointerEventPass.Main)
                        val pos    = event.changes.firstOrNull()?.position ?: continue
                        mousePos   = Vec2(pos.x, pos.y)
                        val prev   = isLeftDown
                        isLeftDown  = event.buttons.isPrimaryPressed
                        isRightDown = event.buttons.isSecondaryPressed

                        // Mausrad-Klick (mittlere Taste) → Item aufheben
                        if (event.buttons.isTertiaryPressed) {
                            val localId = gameState.players.firstOrNull { it.isLocalPlayer }?.id ?: -1
                            if (localId >= 0) gameState = GameEngine.pickupNearby(gameState, localId)
                        }

                        for (change in event.changes) change.consume()
                    }
                }
            }
            // ── Scroll-Rad ────────────────────────────────────────────────────
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val delta = event.changes.firstOrNull()?.scrollDelta ?: continue
                        if (delta.y != 0f) {
                            val localId = gameState.players.firstOrNull { it.isLocalPlayer }?.id ?: -1
                            if (localId >= 0) gameState = GameEngine.scrollSlot(gameState, localId, delta.y < 0)
                        }
                    }
                }
            }
    ) {
        // ── Canvas-Rendering ──────────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = size.width
            val sh = size.height
            screenSize = Vec2(sw, sh)

            with(GameRenderer) {
                renderGame(gameState, sw, sh, isBlind)

                // Minimap (unten rechts)
                val minimapSize = 140f
                renderMinimap(gameState, sw - minimapSize - 12f, sh - minimapSize - 12f, minimapSize)
            }

            // Fadenkreuz
            if (!isBlind) {
                drawLine(Color.White.copy(alpha = 0.5f), Offset(sw / 2 - 10, sh / 2), Offset(sw / 2 + 10, sh / 2), 1.5f)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(sw / 2, sh / 2 - 10), Offset(sw / 2, sh / 2 + 10), 1.5f)
                drawCircle(Color.White.copy(alpha = 0.2f), 4f, center = Offset(sw / 2, sh / 2))
            }
        }

        // ── HUD (Compose-Layer) ───────────────────────────────────────────────
        if (!gameState.isGameOver) {
            GameHUD(
                state = gameState,
                localPlayer = localPlayer,
                onArmorClick = {
                    val player = gameState.players.firstOrNull { it.isLocalPlayer && it.isAlive }
                    if (player != null) {
                        gameState = when (player.inventory.armorSlot) {
                            ArmorType.AGILITY -> GameEngine.dash(gameState, player.id)
                            ArmorType.STEALTH -> GameEngine.activateStealth(gameState, player.id)
                            else -> gameState
                        }
                    }
                }
            )

            // Spawn-Countdown oben in der Mitte
            val spawningPlayer = localPlayer
            if (spawningPlayer?.isSpawning == true) {
                SpawnCountdown(spawningPlayer.spawnTimer)
            }
        }

        // ── Game-Over / Multiplayer Overlay ───────────────────────────────────────────
        if (mode == GameMode.MULTIPLAYER && !gameState.isGameOver) {
            Box(
                modifier = Modifier.padding(16.dp).align(Alignment.TopCenter)
                    .background(Color(0x88000000), RoundedCornerShape(8.dp)).padding(12.dp)
            ) {
                Text("MULTIPLAYER MOCK\n(Local Bot-Game until Network is active)", color = Color.White, textAlign = TextAlign.Center)
            }
        }

        if (gameState.isGameOver) {
            GameOverScreen(state = gameState, onRestart = {
                gameState = createInitialState()
                onRestart()
            })
        }
    }

    LaunchedEffect(Unit) {
        delay(100); focusRequester.requestFocus()
    }
}