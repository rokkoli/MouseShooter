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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.atan2

// ─── Spielbildschirme ─────────────────────────────────────────────────────────
enum class Screen { MENU, GAME }

// ─── Haupt-App ────────────────────────────────────────────────────────────────
@Composable
fun App() {
    var screen by remember { mutableStateOf(Screen.MENU) }
    when (screen) {
        Screen.MENU -> MainMenu(onStart = { screen = Screen.GAME })
        Screen.GAME -> GameScreen(onRestart = { screen = Screen.MENU })
    }
}

// ─── Spielbildschirm ──────────────────────────────────────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameScreen(onRestart: () -> Unit) {
    var gameState   by remember { mutableStateOf(createInitialState()) }
    var mousePos    by remember { mutableStateOf(Vec2(400f, 300f)) }
    var isRightDown by remember { mutableStateOf(false) }
    var isLeftDown  by remember { mutableStateOf(false) }
    var screenSize  by remember { mutableStateOf(Vec2(1280f, 800f)) }

    val focusRequester = remember { FocusRequester() }
    var lastTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // ── Game-Loop ─────────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        while (isActive) {
            val now = System.currentTimeMillis()
            val dt  = ((now - lastTime) / 1000f).coerceAtMost(0.05f)
            lastTime = now

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

                // Schießen (Dauerfeuer bei gedrückter LMB)
                if (isLeftDown) {
                    val localId = gameState.players.firstOrNull { it.isLocalPlayer }?.id ?: -1
                    if (localId >= 0) {
                        gameState = GameEngine.shoot(gameState, localId)
                    }
                }

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
            // ── Tastaturevents ────────────────────────────────────────────────
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val localId = gameState.players.firstOrNull { it.isLocalPlayer }?.id ?: -1
                    when (event.key) {
                        Key.Q -> {
                            if (localId >= 0) {
                                val armor = gameState.players.firstOrNull { it.isLocalPlayer }?.inventory?.armorSlot
                                gameState = when (armor) {
                                    ArmorType.AGILITY -> GameEngine.dash(gameState, localId)
                                    ArmorType.STEALTH -> GameEngine.activateStealth(gameState, localId)
                                    else -> gameState
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
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
            GameHUD(state = gameState, localPlayer = localPlayer)

            // Spawn-Countdown oben in der Mitte
            val spawningPlayer = localPlayer
            if (spawningPlayer?.isSpawning == true) {
                SpawnCountdown(spawningPlayer.spawnTimer)
            }
        }

        // ── Game-Over ─────────────────────────────────────────────────────────
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