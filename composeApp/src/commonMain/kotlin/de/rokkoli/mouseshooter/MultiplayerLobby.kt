package de.rokkoli.mouseshooter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Lobby palette
// ---------------------------------------------------------------------------

private val NEON_CYAN   = Color(0xFF00CCFF)
private val NEON_PINK   = Color(0xFFFF3366)
private val NEON_LIME   = Color(0xFF39FF14)
private val BG_COLOR    = Color(0xFF0A1020)
private val DIM_TEXT    = Color(0xFFAAAAAA)

private val PLAYER_COLORS = listOf(
    Color(0xFF00CCFF),  // P1: Cyan (host)
    Color(0xFFFF4444),  // P2: Red
    Color(0xFF39FF14),  // P3: Lime
    Color(0xFFFFFF00),  // P4: Yellow
)

// ---------------------------------------------------------------------------
// Multiplayer Lobby
// ---------------------------------------------------------------------------

@Composable
fun MultiplayerLobby(
    onBack: () -> Unit,
    onGameReady: (connector: MultiplayerConnector, isHost: Boolean) -> Unit,
) {
    var lobbyMode by remember { mutableStateOf<LobbyMode?>(null) }
    val connector = remember { createMultiplayerConnector() }
    var connState by remember { mutableStateOf(LobbyConnectionState.Idle) }
    var joinCode  by remember { mutableStateOf("") }
    var errorMsg  by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(lobbyMode) {
        if (lobbyMode == LobbyMode.Join) {
            focusRequester.requestFocus()
        }
    }

    // Pulse animation
    var frameCount by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            frameCount += 0.03f
        }
    }
    val pulse = 0.5f + 0.5f * sin(frameCount)

    // Listen for state changes
    LaunchedEffect(connector) {
        connector.onStateChanged { newState ->
            connState = newState
            errorMsg = connector.errorMessage
        }
    }

    // Auto-transition to game when connected (GUEST ONLY)
    LaunchedEffect(connState) {
        if (connState == LobbyConnectionState.Connected && lobbyMode == LobbyMode.Join) {
            onGameReady(connector, false)
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            if (connState != LobbyConnectionState.Connected) {
                connector.disconnect()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(BG_COLOR),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            // Title
            Text(
                text = "MULTIPLAYER",
                style = TextStyle(
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NEON_PINK,
                    letterSpacing = 4.sp,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "P2P Battle Royale",
                color = Color(0xFF888899),
                fontSize = 16.sp,
                letterSpacing = 2.sp,
            )

            Spacer(modifier = Modifier.height(48.dp))

            when {
                // Error state
                connState == LobbyConnectionState.Error -> {
                    Text(
                        text = "VERBINDUNGSFEHLER",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFFFF3333),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMsg ?: "Unbekannter Fehler",
                        fontSize = 12.sp,
                        color = DIM_TEXT,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    LobbyButton("ERNEUT VERSUCHEN", NEON_CYAN) {
                        connector.disconnect()
                        connState = LobbyConnectionState.Idle
                        lobbyMode = null
                    }
                }

                // Choosing mode
                lobbyMode == null -> {
                    LobbyButton("SPIEL ERSTELLEN", NEON_CYAN) {
                        lobbyMode = LobbyMode.Host
                        connector.hostGame()
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    LobbyButton("SPIEL BEITRETEN", NEON_PINK) {
                        lobbyMode = LobbyMode.Join
                    }
                }

                // Host: waiting for guest
                lobbyMode == LobbyMode.Host -> {
                    Text(
                        text = "DEIN RAUMCODE",
                        fontSize = 14.sp,
                        color = DIM_TEXT,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Big room code display
                    Box(
                        modifier = Modifier
                            .border(2.dp, NEON_CYAN.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = connector.roomCode,
                            fontWeight = FontWeight.Bold,
                            fontSize = 48.sp,
                            color = NEON_CYAN,
                            letterSpacing = 12.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "TEILE DIESEN CODE MIT DEINEM GEGNER",
                        fontSize = 11.sp,
                        color = DIM_TEXT,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Animated waiting indicator
                    val dots = ".".repeat(((frameCount * 0.5f).toInt() % 4))
                    Text(
                        text = "SPIELER VERBUNDEN: ${connector.connectedPlayers}/4$dots",
                        fontSize = 14.sp,
                        color = NEON_CYAN,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Player slots
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0 until 4) {
                            val isConnected = i < connector.connectedPlayers
                            val color = if (isConnected) PLAYER_COLORS[i % PLAYER_COLORS.size] else DIM_TEXT
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .border(1.dp, color, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "P${i + 1}",
                                    fontSize = 12.sp,
                                    color = color
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (connector.connectedPlayers >= 2) {
                        LobbyButton("SPIEL STARTEN", NEON_LIME) {
                            onGameReady(connector, true)
                        }
                    } else {
                        Text(
                            text = "MINDESTENS 2 SPIELER BENÖTIGT",
                            fontSize = 12.sp,
                            color = DIM_TEXT,
                        )
                    }
                }

                // Join: enter code
                lobbyMode == LobbyMode.Join && connState == LobbyConnectionState.Idle -> {
                    Text(
                        text = "RAUMCODE EINGEBEN",
                        fontSize = 14.sp,
                        color = DIM_TEXT,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Code input field
                    Box(
                        modifier = Modifier
                            .border(2.dp, NEON_PINK.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicTextField(
                            value = joinCode,
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                        if (joinCode.length == 4) {
                                            connector.joinGame(joinCode)
                                            true
                                        } else false
                                    } else false
                                },
                            onValueChange = { newValue ->
                                val filtered = newValue.uppercase()
                                    .filter { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" }
                                    .take(4)
                                joinCode = filtered
                            },
                            textStyle = TextStyle(
                                fontSize = 48.sp,
                                color = Color.Transparent,
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(Color.Transparent),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.Center) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        for (i in 0 until 4) {
                                            val char = joinCode.getOrNull(i)
                                            val isCurrentFocus = i == joinCode.length
                                            val charToDraw = char?.toString() ?: "_"
                                            val alpha = if (char != null) 1f else if (isCurrentFocus) pulse else 0.3f

                                            Text(
                                                text = charToDraw,
                                                fontSize = 48.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NEON_PINK.copy(alpha = alpha),
                                            )
                                        }
                                    }
                                    Box(modifier = Modifier.matchParentSize()) {
                                        innerTextField()
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (joinCode.length == 4) {
                        LobbyButton("VERBINDEN", NEON_LIME) {
                            connector.joinGame(joinCode)
                        }
                    }
                }

                // Join: connecting
                lobbyMode == LobbyMode.Join && connState == LobbyConnectionState.Connecting -> {
                    val dots = ".".repeat(((frameCount * 0.5f).toInt() % 4))
                    Text(
                        text = "VERBINDE$dots",
                        fontSize = 18.sp,
                        color = NEON_PINK,
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Back button
            if (connState != LobbyConnectionState.Connected || lobbyMode == LobbyMode.Host) {
                LobbyButton("ZURÜCK", DIM_TEXT) {
                    connector.disconnect()
                    onBack()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Internal
// ---------------------------------------------------------------------------

private enum class LobbyMode { Host, Join }

@Composable
private fun LobbyButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(320.dp)
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 36.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = color,
            letterSpacing = 2.sp,
        )
    }
}
