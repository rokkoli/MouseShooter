package de.rokkoli.mouseshooter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.detectDragGestures

// ─── HUD-Farben ───────────────────────────────────────────────────────────────
private val HudBackground = Color(0xCC000000)
private val HudBorder = Color(0x66FFFFFF)
private val HudAccent = Color(0xFF00CCFF)
private val SlotActive = Color(0xFF00CCFF)
private val SlotInactive = Color(0xFF333344)
private val SlotBorder = Color(0xFF555566)

// ─── Hauptspiel-HUD ───────────────────────────────────────────────────────────
@Composable
fun GameHUD(
    state: GameState,
    localPlayer: Player?,
    onArmorClick: () -> Unit = {},
    onExitSpectate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (localPlayer == null) return

    Box(modifier = modifier.fillMaxSize()) {

        // ── Top-Left: Zone-Warnung + Zeit ─────────────────────────────────────
        TopStatusBar(state, localPlayer, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))

        // ── Top-Right: Kill-Count ─────────────────────────────────────────────
        KillCounter(localPlayer, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))

        // ── Center-Right: Kill-Feed ───────────────────────────────────────────
        KillFeedOverlay(state.killFeed, modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp))

        // ── Bottom-Center: Inventar-Slots ─────────────────────────────────────
        InventoryBar(localPlayer, onArmorClick, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))

        // ── Spectator Overlay ─────────────────────────────────────────────────
        if (!localPlayer.isAlive && !state.isGameOver) {
            SpectatorOverlay(
                state = state, 
                onExitSpectate = onExitSpectate, 
                modifier = Modifier.align(Alignment.Center).padding(top = 150.dp)
            )
        }

        // ── Bottom-Right: Minimap-Platzhalter (wird per Canvas gezeichnet) ────
        MinimapLabel(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))

        // ── HP-Balken (Bottom-Left) ────────────────────────────────────────────
        HpBar(localPlayer, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))

        // ── Skill-Cooldown (Rüstung) ──────────────────────────────────────────
        if (localPlayer.inventory.armorSlot != null) {
            SkillIndicator(localPlayer, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 80.dp))
        }
    }
}

@Composable
private fun TopStatusBar(state: GameState, player: Player, modifier: Modifier) {
    val bz = state.battleZone
    val isInZone = player.pos.distanceTo(Vec2(bz.centerX, bz.centerY)) <= bz.currentRadius

    Column(modifier = modifier) {
        // Spieleranzahl
        val alivePlayers = state.players.count { it.isAlive }
        HudChip(text = "$alivePlayers / ${state.players.size}", color = HudAccent, icon = Icons.Default.Person)

        Spacer(Modifier.height(6.dp))

        // Zonenwarnung
        HudChip(
            text = if (isInZone) "In der Zone" else "ZONE VERLASSEN!",
            color = if (isInZone) Color(0xFF44FF44) else Color(0xFFFF3300),
            icon = if (isInZone) Icons.Default.Check else Icons.Default.Warning
        )

        Spacer(Modifier.height(6.dp))

        // Status-Effekte
        val se = player.statusEffects
        if (se.stunTimer > 0f) HudChip("Gestunnt ${se.stunTimer.toInt()}s", Color(0xFF8800FF), Icons.Default.Build)
        if (se.slowTimer > 0f) HudChip("Verlangsamt ${se.slowTimer.toInt()}s", Color(0xFF00CCFF), Icons.Default.Refresh)
        if (se.blindTimer > 0f) HudChip("Geblendet ${se.blindTimer.toInt()}s", Color.White, Icons.Default.Close)
        if (se.invisibleTimer > 0f) HudChip("Unsichtbar ${se.invisibleTimer.toInt()}s", Color(0xFF44CC44), Icons.Default.Face)
        if (se.healRemaining > 0f) HudChip("Heilung läuft", Color(0xFFFF3344), Icons.Default.Favorite)
        if (player.isReloading) HudChip("LADE NACH...", Color(0xFFFFAA00), Icons.Default.Refresh)
    }
}

@Composable
private fun KillCounter(player: Player, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(HudBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Clear, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("${player.kills}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun InventoryBar(player: Player, onArmorClick: () -> Unit, modifier: Modifier) {
    val inv = player.inventory
    val allSlots = buildList {
        add(Triple(inv.meleeSlot?.label ?: "—", inv.meleeSlot?.color, inv.meleeRarity.glowColor))
        inv.gunSlots.forEachIndexed { i, gun -> 
            add(Triple(gun?.label ?: "—", gun?.color, if (gun != null) inv.gunRarities[i].glowColor else null))
        }
        inv.grenadeSlots.forEachIndexed { i, g -> 
            add(Triple(g?.label ?: "—", g?.color, if (g != null) inv.grenadeRarities[i].glowColor else null))
        }
        // Rüstung
        add(Triple(inv.armorSlot?.label ?: "—", inv.armorSlot?.color, inv.armorRarity?.glowColor))
    }

    val clipAmmo = inv.clipAmmo
    val reserveAmmo = inv.reserveAmmo

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Index: 0=Melee, 1-3=Guns, 4-5=Grenades, 6=Rüstung
        allSlots.forEachIndexed { index, (label, color, rarityColor) ->
            val isActive = index == inv.selectedSlotIndex
            val itemColor = color?.let { Color(it) } ?: Color.Transparent
            val rarityCol = rarityColor?.let { Color(it) } ?: Color.Transparent

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Slot-Nummer
                Text(
                    text = "${index + 1}",
                    color = if (isActive) SlotActive else Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(
                            if (rarityCol != Color.Transparent) rarityCol.copy(alpha = 0.25f) else SlotInactive,
                            RoundedCornerShape(8.dp)
                        )
                        .then(if (isActive) Modifier.border(2.5.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (index == 6) Modifier.clickable { onArmorClick() } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (color != null) {
                        // Item vorhanden
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(itemColor, RoundedCornerShape(3.dp))
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = label.take(8),
                                color = if (isActive) Color.White else Color.White.copy(alpha = 0.7f),
                                fontSize = 8.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 9.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                            
                            // Munitions-Anzeige für Schusswaffen
                            if (index in 1..3) {
                                val gun = inv.gunSlots[index - 1]
                                if (gun != null) {
                                    val current = clipAmmo.getOrNull(index - 1) ?: 0
                                    val total = reserveAmmo[gun.ammoType] ?: 0
                                    Text(
                                        text = "$current / $total",
                                        color = if (current == 0) Color.Red else Color.White.copy(alpha = 0.8f),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Text("—", color = Color(0xFF555566), fontSize = 14.sp)
                    }
                }

                // Slot-Kategorie-Label
                val catLabel = when (index) {
                    0 -> "Melee"
                    in 1..3 -> "Schuss"
                    in 4..5 -> "Granate"
                    6 -> "Rüstung"
                    else -> ""
                }
                Text(catLabel, color = Color(0xFF666677), fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun HpBar(player: Player, modifier: Modifier) {
    val hpFrac = (player.hp / player.maxHp).coerceIn(0f, 1f)
    val hpColor = when {
        hpFrac > 0.5f -> Color(0xFF44FF44)
        hpFrac > 0.25f -> Color(0xFFFFAA00)
        else -> Color(0xFFFF4444)
    }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = hpColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                "${player.hp.toInt()} / ${player.maxHp.toInt()}",
                color = hpColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(8.dp)
                .background(Color(0xFF222233), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(hpFrac)
                    .background(hpColor, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun SkillIndicator(player: Player, modifier: Modifier) {
    val armor = player.inventory.armorSlot ?: return
    val se = player.statusEffects
    val cooldown = se.dashCooldown

    val skillName = when (armor) {
        ArmorType.MILITARY -> "Passiv: Resistenz"
        ArmorType.STEALTH -> "Klick: Unsichtbarkeit"
        ArmorType.AGILITY -> "Klick: Dash (${if (cooldown > 0f) "${cooldown.toInt()}s" else "Bereit!"})"
    }

    Box(
        modifier = modifier
            .background(HudBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            skillName,
            color = if (cooldown > 0f) Color.Gray else HudAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MinimapLabel(modifier: Modifier) {
    Box(
        modifier = modifier
            .size(130.dp)
            .background(Color.Transparent)
    )
    // Die eigentliche Minimap wird per DrawScope im Canvas gezeichnet
}

@Composable
private fun HudChip(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
// ─── Kill Feed ────────────────────────────────────────────────────────────────
@Composable
private fun KillFeedOverlay(feed: List<String>, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        for (event in feed) {
            Box(
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(event, color = Color.White, fontSize = 12.sp)
            }
        }
    }
}


// ─── Spawn-Countdown ────────────────────────────────────────────────────────────
@Composable
fun SpawnCountdown(spawnTimer: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 60.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF00CCFF), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Landungspunkt festlegen",
                    color = Color(0xFF00CCFF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Landung in ${spawnTimer.toInt().coerceAtLeast(0) + 1}s • Bewege die Maus um zu landen",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ─── Game Over Screen ─────────────────────────────────────────────────────────
@Composable
fun GameOverScreen(state: GameState, onRestart: () -> Unit) {
    val winner = state.players.firstOrNull { it.id == state.winnerId }
    val localPlayer = state.players.firstOrNull { it.isLocalPlayer }
    val playerWon = localPlayer?.id == state.winnerId

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (playerWon) Icons.Default.Star else Icons.Default.Clear, contentDescription = null, tint = if (playerWon) Color(0xFFFFD700) else Color(0xFFFF4444), modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                if (playerWon) "VICTORY!" else "ELIMINATED",
                color = if (playerWon) Color(0xFFFFD700) else Color(0xFFFF4444),
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(16.dp))
            if (!playerWon && winner != null) {
                Text("Gewinner: Spieler ${winner.id + 1}", color = Color.White, fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
            }
            Text("Kills: ${localPlayer?.kills ?: 0}", color = Color(0xFFCCCCCC), fontSize = 18.sp)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AAFF))
            ) {
                Text("Nochmal spielen", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Hauptmenü ────────────────────────────────────────────────────────────────
@Composable
fun MainMenu(onStartSingleplayer: () -> Unit, onStartMultiplayer: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1020)),
        contentAlignment = Alignment.Center
    ) {
        // Hintergrund-Dekor
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "MOUSE SHOOTER",
                color = Color(0xFF00CCFF),
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
            Text(
                "Top-Down Battle Royale",
                color = Color(0xFF888899),
                fontSize = 18.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(48.dp))

            // Steuerung-Info
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111122)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.width(380.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF00CCFF), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("STEUERUNG", color = Color(0xFF00CCFF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    controlRow("Mausbewegung", "Charakter läuft in Richtung Maus")
                    controlRow("Rechte Maustaste", "Anhalten (stehen bleiben)")
                    controlRow("LMB: Waffe", "Schießen / Angreifen")
                    controlRow("LMB: Granate", "Granate werfen (aktiver Slot)")
                    controlRow("Mausrad scrollen", "Waffe/Item wechseln")
                    controlRow("Mausrad klicken", "Item vom Boden aufheben")
                    controlRow("Klick auf Rüstung", "Rüstungs-Skill aktivieren")
                    Spacer(Modifier.height(10.dp))
                    Text("INFO: Alle Spieler starten mit Fallschirm", color = Color(0xFF4499FF), fontSize = 11.sp)
                    Text("INFO: Seltenere Items spawnen weiter vom Zentrum", color = Color(0xFF666677), fontSize = 11.sp)
                    Text("WARNUNG: Die Kampfzone schrumpft – bleib drin!", color = Color(0xFFFF6644), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onStartSingleplayer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AAFF)),
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("SINGLEPLAYER", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                }

                Button(
                    onClick = onStartMultiplayer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366)),
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("MULTIPLAYER", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                }
            }
        }
    }
}

@Composable
private fun controlRow(key: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            key,
            color = Color(0xFFCCCCFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(160.dp)
        )
        Text(desc, color = Color(0xFF888899), fontSize = 12.sp)
    }
}
@Composable
private fun SpectatorOverlay(state: GameState, onExitSpectate: () -> Unit, modifier: Modifier) {
    val target = state.players.firstOrNull { it.id == state.spectatedPlayerId }
    val name = if (target != null) "Bot ${target.id}" else "Niemanden"
    
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DU BIST ELIMINIERT", color = Color.Red, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Color.Red, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("ZUSCHAUERMODUS", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text("Du schaust gerade zu:", color = Color.Gray, fontSize = 14.sp)
            Text(name, color = HudAccent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onExitSpectate,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444455)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("ZUSCHAUEN BEENDEN", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
