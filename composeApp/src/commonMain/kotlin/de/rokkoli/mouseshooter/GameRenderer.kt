package de.rokkoli.mouseshooter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.*

// ─── Hilfsfunktionen ────────────────────────────────────────────────────────
fun Long.toColor(): Color = Color(this)
fun Long.withAlpha(alpha: Float): Color = Color(this).copy(alpha = alpha)
fun Color.withAlpha(alpha: Float): Color = this.copy(alpha = alpha)

// Weltkoordinaten → Bildschirmkoordinaten (mit Zoom)
fun Vec2.toOffset(camX: Float, camY: Float, screenW: Float, screenH: Float, zoom: Float = 1f): Offset =
    Offset((x - camX) * zoom + screenW / 2, (y - camY) * zoom + screenH / 2)

// ─── GameRenderer ────────────────────────────────────────────────────────────
object GameRenderer {

    fun DrawScope.renderGame(
        state: GameState,
        screenW: Float,
        screenH: Float,
        localPlayerIsBlind: Boolean
    ) {
        val camX  = state.cameraX
        val camY  = state.cameraY
        val zoom  = state.zoomLevel

        // ── Hintergrund: außerhalb der Map = dunkles Void ─────────────────────
        drawRect(color = Color(0xFF060D06), size = Size(screenW, screenH))

        // Map-Fläche
        val mapLeft = (-camX) * zoom + screenW / 2
        val mapTop  = (-camY) * zoom + screenH / 2
        drawRect(color = Color(0xFF0D1D0D), topLeft = Offset(mapLeft, mapTop),
            size = Size(state.mapWidth * zoom, state.mapHeight * zoom))

        // Gras-Raster (nur innerhalb Map)
        drawMapGrid(camX, camY, screenW, screenH, state, zoom)

        // ── Hindernisse ───────────────────────────────────────────────────────
        for (obs in state.obstacles) {
            val tl = obs.pos.toOffset(camX, camY, screenW, screenH, zoom)
            val w = obs.width * zoom; val h = obs.height * zoom
            // Culling
            if (tl.x + w < 0 || tl.x > screenW || tl.y + h < 0 || tl.y > screenH) continue
            drawRect(color = Color(obs.color), topLeft = tl, size = Size(w, h))
            drawRect(color = Color(0xFF222233), topLeft = tl, size = Size(w, h), style = Stroke(1.5f))
            drawRect(color = Color(0x33FFFFFF), topLeft = tl, size = Size(w, 5f * zoom))
        }

        // ── Effekt-Zonen ──────────────────────────────────────────────────────
        for (zone in state.effectZones) {
            val c = zone.pos.toOffset(camX, camY, screenW, screenH, zoom)
            val r = zone.radius * zoom
            drawCircle(color = Color(zone.color).withAlpha(zone.alpha * 0.5f), radius = r, center = c)
            drawCircle(color = Color(zone.color).withAlpha(zone.alpha), radius = r, center = c, style = Stroke(2f))
        }

        // ── Ground Items (mit Glow) ───────────────────────────────────────────
        for (item in state.groundItems) {
            val c = item.pos.toOffset(camX, camY, screenW, screenH, zoom)
            if (c.x < -60 || c.x > screenW + 60 || c.y < -60 || c.y > screenH + 60) continue

            val glowPhase = when (item) {
                is GroundItem.WeaponItem  -> item.glowPhase
                is GroundItem.GrenadeItem -> item.glowPhase
                is GroundItem.ArmorItem   -> item.glowPhase
                is GroundItem.MedkitItem  -> item.glowPhase
            }
            val glowAlpha  = (sin(glowPhase) * 0.4f + 0.5f).coerceIn(0f, 1f)
            val glowRadius = (14f + sin(glowPhase) * 4f) * zoom

            val glowColor = Color(item.rarity.glowColor).withAlpha(glowAlpha * 0.7f)
            drawCircle(color = glowColor, radius = glowRadius + 6f * zoom, center = c)
            drawCircle(color = glowColor.copy(alpha = glowAlpha * 0.3f), radius = glowRadius + 14f * zoom, center = c)

            when (item) {
                is GroundItem.WeaponItem  -> drawWeaponIcon(c, item.weaponType, glowRadius)
                is GroundItem.GrenadeItem -> drawGrenadeIcon(c, item.grenadeType, glowRadius)
                is GroundItem.ArmorItem   -> drawArmorIcon(c, item.armorType, glowRadius)
                is GroundItem.MedkitItem  -> drawMedkitIcon(c, glowRadius)
            }
        }

        // ── Geworfene Granaten ────────────────────────────────────────────────
        for (g in state.grenades) {
            val c = g.pos.toOffset(camX, camY, screenW, screenH, zoom)
            drawCircle(color = Color(g.grenadeType.color), radius = 7f * zoom, center = c)
            drawCircle(color = Color.White.copy(alpha = 0.5f), radius = 7f * zoom, center = c, style = Stroke(1.5f))
        }

        // ── Explosionen ───────────────────────────────────────────────────────
        for (exp in state.explosions) {
            if (exp.timer >= exp.duration) continue
            val c = exp.pos.toOffset(camX, camY, screenW, screenH, zoom)
            val progress = exp.timer / exp.duration
            val r = exp.maxRadius * sqrt(progress) * zoom
            val alpha = (1f - progress).coerceIn(0f, 1f)
            drawCircle(color = Color(exp.color).withAlpha(alpha * 0.6f), radius = r, center = c)
            drawCircle(color = Color.White.withAlpha(alpha * 0.4f), radius = r * 0.4f, center = c)
        }

        // ── Projektile ────────────────────────────────────────────────────────
        for (proj in state.projectiles) {
            val c = proj.pos.toOffset(camX, camY, screenW, screenH, zoom)
            if (c.x < -20 || c.x > screenW + 20 || c.y < -20 || c.y > screenH + 20) continue
            val r = proj.radius * zoom
            drawCircle(color = Color(proj.color), radius = r + 2f, center = c)
            val trailDir = proj.velocity.normalized() * (-r * 3f)
            drawLine(color = Color(proj.color).withAlpha(0.4f), start = c,
                end = Offset(c.x + trailDir.x, c.y + trailDir.y), strokeWidth = r * 1.5f)
        }

        // ── Melee-Visualisierung ──────────────────────────────────────────────
        for (swing in state.meleeSwings) {
            val start = swing.pos.toOffset(camX, camY, screenW, screenH, zoom)
            val end   = (swing.pos + swing.direction * swing.range).toOffset(camX, camY, screenW, screenH, zoom)
            val alpha = swing.timer / 0.15f
            drawLine(color = Color.White.withAlpha(alpha.coerceIn(0f, 1f)), start = start, end = end, strokeWidth = 3f)
            drawCircle(color = Color.White.withAlpha(alpha * 0.5f), radius = 12f, center = end)
        }

        // ── Spieler ───────────────────────────────────────────────────────────
        for (player in state.players) {
            if (!player.isAlive) continue
            val c = player.pos.toOffset(camX, camY, screenW, screenH, zoom)
            if (c.x < -80 || c.x > screenW + 80 || c.y < -80 || c.y > screenH + 80) continue

            val isInvisible = player.statusEffects.invisibleTimer > 0f && !player.isLocalPlayer
            if (isInvisible) continue

            val playerAlpha = if (player.statusEffects.invisibleTimer > 0f && player.isLocalPlayer) 0.4f else 1f
            val radius = PLAYER_RADIUS * zoom

            // Fallschirm zeichnen
            if (player.isSpawning) {
                drawParachute(c, radius, player.spawnTimer, Color(player.color).withAlpha(playerAlpha))
                continue  // keinen normalen Spielerkörper zeichnen während Spawn
            }

            // Schatten
            drawCircle(color = Color.Black.copy(alpha = 0.3f * playerAlpha), radius = radius + 3f,
                center = Offset(c.x + 3, c.y + 3))
            // Körper
            drawCircle(color = Color(player.color).withAlpha(playerAlpha), radius = radius, center = c)
            // Rand
            val borderColor = if (player.isLocalPlayer) Color.White.withAlpha(playerAlpha) else Color(0xFF888888).withAlpha(playerAlpha)
            drawCircle(color = borderColor, radius = radius, center = c, style = Stroke(2.5f))
            // Blickrichtung
            val frontX = c.x + cos(player.rotation) * radius
            val frontY = c.y + sin(player.rotation) * radius
            drawCircle(color = Color.White.withAlpha(0.8f * playerAlpha), radius = 4f, center = Offset(frontX, frontY))

            // HP-Balken
            val hpBarW = radius * 2.5f; val hpBarH = 4f
            val hpBarX = c.x - hpBarW / 2; val hpBarY = c.y - radius - 10f
            val hpFrac = (player.hp / player.maxHp).coerceIn(0f, 1f)
            drawRect(color = Color(0xFF333333), topLeft = Offset(hpBarX, hpBarY), size = Size(hpBarW, hpBarH))
            val hpColor = when { hpFrac > 0.5f -> Color(0xFF44FF44); hpFrac > 0.25f -> Color(0xFFFFAA00); else -> Color(0xFFFF4444) }
            drawRect(color = hpColor, topLeft = Offset(hpBarX, hpBarY), size = Size(hpBarW * hpFrac, hpBarH))

            // Status-Effekt-Punkte
            var iconX = c.x - 8f; val iconY = hpBarY - 8f
            val se = player.statusEffects
            if (se.stunTimer > 0f)  { drawCircle(color = Color(0xFF8800FF), radius = 4f, center = Offset(iconX, iconY)); iconX += 10f }
            if (se.slowTimer > 0f)  { drawCircle(color = Color(0xFF00CCFF), radius = 4f, center = Offset(iconX, iconY)); iconX += 10f }
        }

        // ── Kampfzone ─────────────────────────────────────────────────────────
        val bz = state.battleZone
        val bzCenter = Vec2(bz.centerX, bz.centerY).toOffset(camX, camY, screenW, screenH, zoom)
        val bzR = bz.currentRadius * zoom
        drawCircle(color = Color(0xFFFF2200).copy(alpha = 0.06f), radius = bzR, center = bzCenter)
        drawCircle(color = Color(0xFFFF2200).copy(alpha = 0.6f),  radius = bzR, center = bzCenter, style = Stroke(3f))
        val pulse = sin(state.gameTime * 3f) * 0.2f + 0.8f
        drawCircle(color = Color(0xFFFF4400).copy(alpha = 0.15f * pulse), radius = bzR + 20f * zoom * pulse, center = bzCenter, style = Stroke(8f))

        // ── Blindheits-Overlay ────────────────────────────────────────────────
        if (localPlayerIsBlind) {
            drawRect(color = Color.White.copy(alpha = 0.95f), size = Size(screenW, screenH))
        }
    }

    // ── Fallschirm ────────────────────────────────────────────────────────────
    private fun DrawScope.drawParachute(center: Offset, radius: Float, spawnTimer: Float, playerColor: Color) {
        val progress = (1f - (spawnTimer / 3.5f)).coerceIn(0f, 1f)  // 0=oben, 1=unten
        val bobOffset = sin(spawnTimer * 8f) * 3f  // leichtes Schaukeln

        // Spieler-Körper unten
        val bodyY = center.y + bobOffset
        drawCircle(color = Color.Black.copy(alpha = 0.2f), radius = radius + 2f, center = Offset(center.x + 2, bodyY + 2))
        drawCircle(color = playerColor, radius = radius, center = Offset(center.x, bodyY))
        drawCircle(color = Color.White.copy(alpha = 0.6f), radius = radius, center = Offset(center.x, bodyY), style = Stroke(2f))

        // Seile
        val parasailTop = bodyY - radius * 4.5f
        val parasailWidth = radius * 3.5f
        drawLine(Color.White.copy(alpha = 0.6f), Offset(center.x, bodyY - radius),      Offset(center.x - parasailWidth * 0.9f, parasailTop), strokeWidth = 1.2f)
        drawLine(Color.White.copy(alpha = 0.6f), Offset(center.x, bodyY - radius),      Offset(center.x + parasailWidth * 0.9f, parasailTop), strokeWidth = 1.2f)
        drawLine(Color.White.copy(alpha = 0.4f), Offset(center.x, bodyY - radius),      Offset(center.x - parasailWidth * 0.4f, parasailTop), strokeWidth = 1f)
        drawLine(Color.White.copy(alpha = 0.4f), Offset(center.x, bodyY - radius),      Offset(center.x + parasailWidth * 0.4f, parasailTop), strokeWidth = 1f)

        // Fallschirm-Bogen (halbe Ellipse)
        val parasailColor = playerColor.copy(alpha = 0.8f)
        val parasailH     = radius * 2.2f
        // Zeichne Fallschirm als mehrere Segmente (approximierte Ellipse)
        for (seg in 0..5) {
            val a1 = PI.toFloat() + seg * PI.toFloat() / 6
            val a2 = PI.toFloat() + (seg + 1) * PI.toFloat() / 6
            val x1 = center.x + cos(a1) * parasailWidth
            val y1 = parasailTop + sin(a1) * parasailH + parasailH
            val x2 = center.x + cos(a2) * parasailWidth
            val y2 = parasailTop + sin(a2) * parasailH + parasailH
            drawLine(parasailColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = 3f)
        }
        // Fläche (große Ellipse als Kreis approximiert)
        drawOval(color = parasailColor.copy(alpha = 0.35f),
            topLeft = Offset(center.x - parasailWidth, parasailTop),
            size = Size(parasailWidth * 2f, parasailH * 2f))
        drawOval(color = parasailColor,
            topLeft = Offset(center.x - parasailWidth, parasailTop),
            size = Size(parasailWidth * 2f, parasailH * 2f),
            style = Stroke(2.5f))
    }

    // ── Map-Raster ────────────────────────────────────────────────────────────
    private fun DrawScope.drawMapGrid(camX: Float, camY: Float, screenW: Float, screenH: Float, state: GameState, zoom: Float) {
        val gridSize = 200f * zoom
        // Modulo muss immer positiv sein – Kotlin % kann negativ sein
        val rawOffX = (-camX * zoom + screenW / 2) % gridSize
        val rawOffY = (-camY * zoom + screenH / 2) % gridSize
        val offsetX = if (rawOffX < 0) rawOffX + gridSize else rawOffX
        val offsetY = if (rawOffY < 0) rawOffY + gridSize else rawOffY

        var gx = offsetX - gridSize
        while (gx <= screenW + gridSize) {
            var gy = offsetY - gridSize
            while (gy <= screenH + gridSize) {
                // Weltposition dieser Gitterzelle
                val wx = camX + (gx - screenW / 2) / zoom
                val wy = camY + (gy - screenH / 2) / zoom
                // Nur zeichnen wenn innerhalb der Map
                if (wx >= 0 && wx < state.mapWidth && wy >= 0 && wy < state.mapHeight) {
                    val cx = (wx / 200f).toInt(); val cy = (wy / 200f).toInt()
                    val color = if ((cx + cy) % 2 == 0) Color(0xFF1E341E) else Color(0xFF1A2E1A)
                    drawRect(color = color, topLeft = Offset(gx, gy), size = Size(gridSize, gridSize))
                }
                gy += gridSize
            }
            gx += gridSize
        }
    }

    // ── Icons ─────────────────────────────────────────────────────────────────
    private fun DrawScope.drawWeaponIcon(center: Offset, weapon: WeaponType, radius: Float) {
        val col = Color(weapon.color)
        drawCircle(col.copy(alpha = 0.3f), radius, center)
        if (weapon.isMelee) {
            drawLine(col, Offset(center.x - radius * 0.5f, center.y + radius * 0.5f), Offset(center.x + radius * 0.5f, center.y - radius * 0.5f), 3f)
        } else {
            drawLine(col, center, Offset(center.x + radius, center.y), 3f)
            drawCircle(col, 5f, center)
        }
    }

    private fun DrawScope.drawGrenadeIcon(center: Offset, grenade: GrenadeType, radius: Float) {
        drawCircle(Color(grenade.color).copy(alpha = 0.3f), radius, center)
        drawCircle(Color(grenade.color), 6f, center)
        drawLine(Color(grenade.color), center, Offset(center.x, center.y - radius * 0.6f), 2.5f)
    }

    private fun DrawScope.drawArmorIcon(center: Offset, armor: ArmorType, radius: Float) {
        val col = Color(armor.color)
        drawCircle(col.copy(alpha = 0.3f), radius, center)
        drawRect(col.copy(alpha = 0.8f), Offset(center.x - 6f, center.y - 7f), Size(12f, 14f))
    }

    private fun DrawScope.drawMedkitIcon(center: Offset, radius: Float) {
        val col = Color.White
        drawCircle(col.copy(alpha = 0.7f), radius, center)
        val red = Color.Red
        drawRect(red, Offset(center.x - 2f, center.y - 5f), Size(4f, 10f))
        drawRect(red, Offset(center.x - 5f, center.y - 2f), Size(10f, 4f))
    }

    // ── Minimap ───────────────────────────────────────────────────────────────
    fun DrawScope.renderMinimap(state: GameState, mapX: Float, mapY: Float, mapSize: Float) {
        val scaleX = mapSize / state.mapWidth
        val scaleY = mapSize / state.mapHeight

        drawRect(color = Color.Black.copy(alpha = 0.7f), topLeft = Offset(mapX, mapY), size = Size(mapSize, mapSize))
        drawRect(color = Color.White.copy(alpha = 0.3f), topLeft = Offset(mapX, mapY), size = Size(mapSize, mapSize), style = Stroke(1f))

        for (obs in state.obstacles) {
            drawRect(color = Color(obs.color).copy(alpha = 0.5f),
                topLeft = Offset(mapX + obs.pos.x * scaleX, mapY + obs.pos.y * scaleY),
                size = Size(obs.width * scaleX, obs.height * scaleY))
        }

        val bz = state.battleZone
        drawCircle(color = Color(0xFFFF2200).copy(alpha = 0.4f), radius = bz.currentRadius * scaleX,
            center = Offset(mapX + bz.centerX * scaleX, mapY + bz.centerY * scaleY), style = Stroke(1f))

        for (player in state.players) {
            if (!player.isAlive) continue
            val px = mapX + player.pos.x * scaleX
            val py = mapY + player.pos.y * scaleY
            val dotSize = if (player.isLocalPlayer) 4f else 2.5f
            // Fallschirm-Symbol auf Minimap
            val dotColor = if (player.isSpawning) Color.White else Color(player.color)
            drawCircle(color = dotColor, radius = dotSize, center = Offset(px, py))
        }
    }
}
