package de.rokkoli.mouseshooter

import kotlin.math.*
import kotlin.random.Random

// ─── Konstanten ──────────────────────────────────────────────────────────────
const val PLAYER_SPEED = 180f
const val PLAYER_RADIUS = 18f
const val BOT_COUNT = 7
const val MIN_WEAPON_SPAWN_DIST = 1400f

// ─── MapGenerator ────────────────────────────────────────────────────────────
object MapGenerator {
    fun generate(mapW: Float, mapH: Float, random: kotlin.random.Random = kotlin.random.Random): Pair<List<Obstacle>, List<GroundItem>> {
        val obstacles = mutableListOf<Obstacle>()
        val items = mutableListOf<GroundItem>()
        var idCounter = 0
        val center = Vec2(mapW / 2, mapH / 2)
        val cols = 12
        val rows = 12
        val cellW = (mapW - 800f) / cols
        val cellH = (mapH - 800f) / rows
        val startX = 400f
        val startY = 400f

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val cx = startX + col * cellW + cellW / 2f
                val cy = startY + row * cellH + cellH / 2f
                val pos = Vec2(cx, cy)
                
                // Zentrum frei lassen für das Endgame
                if (pos.distanceTo(center) < 600f) continue
                
                // 30% Chance, Zelle komplett freizulassen
                if (random.nextFloat() < 0.3f) continue

                val choice = random.nextInt(5)
                when (choice) {
                    0 -> { // Einfacher Block (Haus)
                        val w = random.nextFloat() * 150f + 100f
                        val h = random.nextFloat() * 150f + 100f
                        obstacles.add(Obstacle(pos, w, h, 0xFF665544L))
                    }
                    1 -> { // L-Wand
                        obstacles.add(Obstacle(pos, 16f, 250f, 0xFF778899L))
                        obstacles.add(Obstacle(Vec2(pos.x + 120f, pos.y + 120f), 240f, 16f, 0xFF778899L))
                    }
                    2 -> { // U-Form (Bunker/Deckung)
                        obstacles.add(Obstacle(Vec2(pos.x - 100f, pos.y), 16f, 180f, 0xFF556655L))
                        obstacles.add(Obstacle(Vec2(pos.x + 100f, pos.y), 16f, 180f, 0xFF556655L))
                        obstacles.add(Obstacle(Vec2(pos.x, pos.y + 90f), 216f, 16f, 0xFF556655L))
                    }
                    3 -> { // Kleine Pfeiler / Kisten
                        obstacles.add(Obstacle(Vec2(pos.x - 60f, pos.y - 60f), 40f, 40f, 0xFF554444L))
                        obstacles.add(Obstacle(Vec2(pos.x + 60f, pos.y - 60f), 40f, 40f, 0xFF554444L))
                        obstacles.add(Obstacle(Vec2(pos.x - 60f, pos.y + 60f), 40f, 40f, 0xFF554444L))
                        obstacles.add(Obstacle(Vec2(pos.x + 60f, pos.y + 60f), 40f, 40f, 0xFF554444L))
                    }
                    4 -> { // Lange Mauer horizontal oder vertikal
                        if (random.nextBoolean()) {
                            obstacles.add(Obstacle(pos, 400f, 16f, 0xFFAABBCCL))
                        } else {
                            obstacles.add(Obstacle(pos, 16f, 400f, 0xFFAABBCCL))
                        }
                    }
                }
            }
        }

        val maxDist = sqrt(mapW * mapW + mapH * mapH) / 2f

        // Waffen: 25 Stück, min. MIN_WEAPON_SPAWN_DIST vom Zentrum
        repeat(25) {
            val angle = random.nextFloat() * 2 * PI.toFloat()
            val dist  = MIN_WEAPON_SPAWN_DIST + random.nextFloat() * (maxDist * 0.88f - MIN_WEAPON_SPAWN_DIST)
            val pos   = Vec2(center.x + cos(angle) * dist, center.y + sin(angle) * dist).clampToMap(mapW, mapH)
            val rarity = rarityFromDistance(dist, maxDist, random)
            val pool = if (rarity.ordinal >= Rarity.EPIC.ordinal)
                listOf(WeaponType.SMG, WeaponType.FLAMETHROWER, WeaponType.ROCKET_LAUNCHER, WeaponType.SHOTGUN, WeaponType.SNIPER, WeaponType.MINIGUN)
            else if (rarity.ordinal >= Rarity.RARE.ordinal)
                listOf(WeaponType.SMG, WeaponType.SHOTGUN)
            else listOf(WeaponType.PISTOL, WeaponType.KNIFE, WeaponType.LONG_KNIFE, WeaponType.SHOTGUN)
            items.add(GroundItem.WeaponItem(idCounter++, pos, pool.random(random), rarity))
        }

        // Granaten: 8 Stück
        repeat(8) {
            val angle = random.nextFloat() * 2 * PI.toFloat()
            val dist  = 900f + random.nextFloat() * (maxDist * 0.85f - 900f)
            val pos   = Vec2(center.x + cos(angle) * dist, center.y + sin(angle) * dist).clampToMap(mapW, mapH)
            items.add(GroundItem.GrenadeItem(idCounter++, pos, GrenadeType.values().random(random), rarityFromDistance(dist, maxDist, random)))
        }

        // Rüstungen: 5 Stück
        repeat(5) {
            val angle = random.nextFloat() * 2 * PI.toFloat()
            val dist  = MIN_WEAPON_SPAWN_DIST + random.nextFloat() * (maxDist * 0.8f - MIN_WEAPON_SPAWN_DIST)
            val pos   = Vec2(center.x + cos(angle) * dist, center.y + sin(angle) * dist).clampToMap(mapW, mapH)
            items.add(GroundItem.ArmorItem(idCounter++, pos, ArmorType.values().random(random), rarityFromDistance(dist, maxDist, random)))
        }

        // Nahkampf-Waffen etwas näher – für frühe Engagement
        for (i in 0..2) {
            val a = i * (2 * PI.toFloat() / 3)
            val d = 700f + random.nextFloat() * 400f
            val pos = Vec2(center.x + cos(a) * d, center.y + sin(a) * d).clampToMap(mapW, mapH)
            items.add(GroundItem.WeaponItem(idCounter++, pos,
                listOf(WeaponType.KNIFE, WeaponType.LONG_KNIFE, WeaponType.BOXING_GLOVES).random(random), Rarity.COMMON))
        }

        // Medkits: 12 Stück als Granaten
        repeat(12) {
            val angle = random.nextFloat() * 2 * PI.toFloat()
            val dist  = 600f + random.nextFloat() * (maxDist * 0.9f - 600f)
            val pos   = Vec2(center.x + cos(angle) * dist, center.y + sin(angle) * dist).clampToMap(mapW, mapH)
            items.add(GroundItem.GrenadeItem(idCounter++, pos, GrenadeType.MEDKIT, rarityFromDistance(dist, maxDist, random)))
        }

        return Pair(obstacles, items)
    }
}

fun Vec2.clampToMap(w: Float, h: Float) = Vec2(x.coerceIn(50f, w - 50f), y.coerceIn(50f, h - 50f))

// ─── Initial-State ────────────────────────────────────────────────────────────
fun createInitialState(): GameState {
    val mapW = 9000f
    val mapH = 9000f
    val center = Vec2(mapW / 2, mapH / 2)
    val (obstacles, items) = MapGenerator.generate(mapW, mapH)

    val players = mutableListOf<Player>()

    players.add(Player(
        id = 0, pos = center, hp = 100f, isLocalPlayer = true,
        inventory = Inventory(meleeSlot = WeaponType.FISTS, gunSlots = listOf(null, null, null), selectedSlotIndex = 0),
        color = 0xFF00CCFF, isSpawning = true, spawnTimer = 3.5f
    ))

    val botColors = listOf(0xFFFF4444L, 0xFFFF8800L, 0xFFFF00AAL, 0xFF00FF88L, 0xFFFFFF00L, 0xFFAA00FFL, 0xFF00AAFFL)
    for (i in 1..BOT_COUNT) {
        // Jeder Bot hat eine einzigartige Streurichtung
        val spreadAngle = (i.toFloat() / BOT_COUNT) * 2 * PI.toFloat()
        val spawnDist = 55f + i * 12f
        val botPos = Vec2(center.x + cos(spreadAngle) * spawnDist, center.y + sin(spreadAngle) * spawnDist)
        players.add(Player(
            id = i, pos = botPos, hp = 100f, isLocalPlayer = false, isBot = true,
            inventory = Inventory(meleeSlot = WeaponType.FISTS, gunSlots = listOf(null, null, null), selectedSlotIndex = 0),
            color = botColors[(i - 1) % botColors.size],
            isSpawning = true, spawnTimer = 3.5f + i * 0.1f,
            spreadAngle = spreadAngle,        // Richtung in die gestreut wird
            wanderAngle = spreadAngle         // initialer Wanderwinkel = Streurichtung
        ))
    }

    return GameState(
        players = players, groundItems = items, obstacles = obstacles,
        battleZone = BattleZone(4000f, 150f, center.x, center.y, shrinkRate = 4f),
        mapWidth = mapW, mapHeight = mapH,
        cameraX = center.x, cameraY = center.y,
        nextId = 2000, zoomLevel = 1.8f
    )
}

// ─── Game Engine ──────────────────────────────────────────────────────────────
object GameEngine {

    fun update(state: GameState, dt: Float, mousePos: Vec2, isRightMouseDown: Boolean, viewport: Vec2): GameState {
        if (state.isGameOver) return state
        var s = state.copy(gameTime = state.gameTime + dt)

        // Glow-Animationen
        s = s.copy(groundItems = s.groundItems.map { item ->
            when (item) {
                is GroundItem.WeaponItem  -> item.copy(glowPhase = (item.glowPhase + dt * 2f) % (2 * PI.toFloat()))
                is GroundItem.GrenadeItem -> item.copy(glowPhase = (item.glowPhase + dt * 2f) % (2 * PI.toFloat()))
                is GroundItem.ArmorItem   -> item.copy(glowPhase = (item.glowPhase + dt * 2f) % (2 * PI.toFloat()))
            }
        })

        s = updateSpawnPhase(s, dt)
        s = updatePlayers(s, dt, mousePos, isRightMouseDown, viewport)
        s = updateProjectiles(s, dt)
        s = updateGrenades(s, dt)
        s = updateExplosions(s, dt)
        s = updateEffectZones(s, dt)
        s = updateMeleeSwings(s, dt)
        s = updateBattleZone(s, dt)
        s = checkZoneDamage(s, dt)
        s = updateBots(s, dt)

        // Heal over Time & Loot Drop
        var nId = s.nextId
        val nextItems = s.groundItems.toMutableList()
        s = s.copy(players = s.players.map { p ->
            var upd = p
            // 1) Heal over time
            if (upd.isAlive && upd.statusEffects.healRemaining > 0f) {
                val healAmt = 4f * dt // 10 Sekunden für 40 HP
                val actual = healAmt.coerceAtMost(upd.statusEffects.healRemaining)
                upd = upd.copy(
                    hp = (upd.hp + actual).coerceAtMost(upd.maxHp),
                    statusEffects = upd.statusEffects.copy(healRemaining = upd.statusEffects.healRemaining - actual)
                )
            }
            // 2) Drop Loot wenn gestorben
            if (!upd.isAlive && !upd.hasDroppedLoot) {
                upd.inventory.gunSlots.filterNotNull().forEach { w ->
                    nextItems.add(GroundItem.WeaponItem(nId++, upd.pos + Vec2(Random.nextFloat()*40-20, Random.nextFloat()*40-20), w, Rarity.COMMON))
                }
                if (upd.inventory.meleeSlot != WeaponType.FISTS && upd.inventory.meleeSlot != null) {
                    nextItems.add(GroundItem.WeaponItem(nId++, upd.pos + Vec2(Random.nextFloat()*40-20, Random.nextFloat()*40-20), upd.inventory.meleeSlot!!, Rarity.COMMON))
                }
                upd = upd.copy(hasDroppedLoot = true)
            }
            upd
        }, groundItems = nextItems, nextId = nId)

        // Kill Feed (Todesliste)
        val newKills = mutableListOf<String>()
        val origAlive = state.players.filter { it.isAlive }
        for (dead in origAlive) {
            val nowDead = s.players.firstOrNull { it.id == dead.id }?.isAlive == false
            if (nowDead) {
                val newP = s.players.first { it.id == dead.id }
                val killer = s.players.firstOrNull { it.id == newP.lastDamagedBy }
                
                val killerName = if (killer?.isLocalPlayer == true) "Du hast" else if (killer != null) "Bot ${killer.id} hat" else "Die Zone hat"
                val victimName = if (dead.isLocalPlayer) "dich" else "Bot ${dead.id}"
                newKills.add("$killerName $victimName eliminiert")
                
                if (killer != null && killer.id != dead.id) {
                    s = s.copy(players = s.players.map { if (it.id == killer.id) it.copy(kills = it.kills + 1) else it })
                }
            }
        }
        if (newKills.isNotEmpty()) {
            val updatedFeed = (s.killFeed + newKills).takeLast(5)
            s = s.copy(killFeed = updatedFeed)
        }

        // Kamera
        val local = s.players.firstOrNull { it.isLocalPlayer && it.isAlive }
        if (local != null) s = s.copy(cameraX = local.pos.x, cameraY = local.pos.y)

        // Game Over
        val alive = s.players.filter { it.isAlive }
        if (alive.size <= 1 && s.players.size > 1)
            s = s.copy(isGameOver = true, winnerId = alive.firstOrNull()?.id ?: -1)

        return s
    }

    // ── Fallschirm-Timer ──────────────────────────────────────────────────────
    private fun updateSpawnPhase(state: GameState, dt: Float): GameState {
        return state.copy(players = state.players.map { p ->
            if (!p.isSpawning) return@map p
            val t = p.spawnTimer - dt
            if (t <= 0f) p.copy(isSpawning = false, spawnTimer = 0f) else p.copy(spawnTimer = t)
        })
    }

    // ── Lokaler Spieler ───────────────────────────────────────────────────────
    private fun updatePlayers(state: GameState, dt: Float, mousePos: Vec2, isRightMouseDown: Boolean, viewport: Vec2): GameState {
        val updated = state.players.map { player ->
            if (!player.isAlive) return@map player
            var p = player

            // ── Statuseffekte & Cooldowns ticken (FÜR ALLE SPIELER!) ──────────
            val se = p.statusEffects
            val newSe = se.copy(
                stunTimer      = (se.stunTimer      - dt).coerceAtLeast(0f),
                slowTimer      = (se.slowTimer      - dt).coerceAtLeast(0f),
                blindTimer     = (se.blindTimer     - dt).coerceAtLeast(0f),
                invisibleTimer = (se.invisibleTimer - dt).coerceAtLeast(0f),
                dashCooldown   = (se.dashCooldown   - dt).coerceAtLeast(0f),
                dashTimer      = (se.dashTimer      - dt).coerceAtLeast(0f)
            )
            p = p.copy(statusEffects = newSe, fireCooldown = (p.fireCooldown - dt).coerceAtLeast(0f))

            if (!p.isLocalPlayer) return@map p // Nur lokaler Spieler verarbeitet Maus/Keyboard hier
            if (newSe.stunTimer > 0f) return@map p  // Stun: kein Input

            // Mausrichtung (zoom-korrigiert)
            val zoom = state.zoomLevel
            val toMouse = (mousePos - Vec2(viewport.x / 2, viewport.y / 2)) * (1f / zoom)
            val dist = toMouse.length()

            // Bewegung erlaubt AUCH während Spawn (nur Schießen gesperrt)
            if (!isRightMouseDown && dist > 5f) {
                val dir = toMouse.normalized()
                val speedMod = when {
                    p.isSpawning      -> 0.5f          // langsamer beim Landen
                    newSe.slowTimer > 0f -> newSe.slowFactor
                    else              -> 1f
                }
                val dashVel = if (newSe.dashTimer > 0f) newSe.dashVelocity else Vec2(0f, 0f)
                val vel = dir * (PLAYER_SPEED * speedMod) + dashVel
                var newPos = p.pos + vel * dt
                newPos = resolveObstacleCollision(newPos, state.obstacles, PLAYER_RADIUS)
                newPos = newPos.clampToMap(state.mapWidth, state.mapHeight)
                p = p.copy(pos = newPos, velocity = vel, rotation = atan2(dir.y, dir.x))
            } else {
                p = p.copy(velocity = Vec2(0f, 0f))
                if (dist > 5f) p = p.copy(rotation = atan2(toMouse.y, toMouse.x))
            }
            p
        }
        return state.copy(players = updated)
    }

    // ── Projektile ────────────────────────────────────────────────────────────
    private fun updateProjectiles(state: GameState, dt: Float): GameState {
        val remaining = mutableListOf<Projectile>()
        val newExp = mutableListOf<Explosion>()
        val players = state.players.toMutableList()

        for (proj in state.projectiles) {
            val p = proj.copy(pos = proj.pos + proj.velocity * dt, lifeTime = proj.lifeTime - dt)
            fun addExp() { if (p.isExplosive) newExp.add(Explosion(p.pos, p.explosionRadius, damage = p.damage)) }
            if (p.lifeTime <= 0f)                                                  { addExp(); continue }
            if (state.obstacles.any { it.intersectsCircle(p.pos, p.radius) })     { addExp(); continue }
            if (p.pos.x < 0 || p.pos.x > state.mapWidth || p.pos.y < 0 || p.pos.y > state.mapHeight) { addExp(); continue }
            var hit = false
            for (i in players.indices) {
                val t = players[i]; if (!t.isAlive || t.isSpawning || t.id == p.ownerId) continue
                if (t.pos.distanceTo(p.pos) < PLAYER_RADIUS + p.radius) {
                    val newHp = t.hp - p.damage
                    players[i] = t.copy(hp = newHp.coerceAtLeast(0f), isAlive = newHp > 0f, lastDamagedBy = p.ownerId)
                    addExp(); hit = true; break
                }
            }
            if (!hit) remaining.add(p)
        }
        return state.copy(projectiles = remaining, players = players, explosions = state.explosions + newExp)
    }

    // ── Explosionen ───────────────────────────────────────────────────────────
    private fun updateExplosions(state: GameState, dt: Float): GameState {
        val players = state.players.toMutableList()
        val remaining = mutableListOf<Explosion>()
        for (exp in state.explosions) {
            val prog = (exp.timer + dt) / exp.duration
            val newExp = exp.copy(timer = exp.timer + dt, currentRadius = exp.maxRadius * sqrt(prog.coerceAtMost(1f)))
            if (!exp.hasDealtDamage && exp.damage > 0f) {
                for (i in players.indices) {
                    val t = players[i]; if (!t.isAlive || t.isSpawning) continue
                    val dist = t.pos.distanceTo(exp.pos)
                    if (dist < exp.maxRadius + PLAYER_RADIUS) {
                        val falloff = 1f - (dist / (exp.maxRadius + PLAYER_RADIUS)).coerceIn(0f, 1f)
                        val newHp = t.hp - exp.damage * falloff
                        players[i] = t.copy(hp = newHp.coerceAtLeast(0f), isAlive = newHp > 0f,
                            velocity = t.velocity + (t.pos - exp.pos).normalized() * 300f * falloff,
                            lastDamagedBy = exp.ownerId)
                    }
                }
            }
            if (newExp.timer < newExp.duration) remaining.add(newExp.copy(hasDealtDamage = true))
        }
        return state.copy(explosions = remaining, players = players)
    }

    // ── Granaten ──────────────────────────────────────────────────────────────
    private fun updateGrenades(state: GameState, dt: Float): GameState {
        val remaining = mutableListOf<ThrownGrenade>()
        val newExp = mutableListOf<Explosion>(); val newZones = mutableListOf<EffectZone>()
        var nextId = state.nextId
        val players = state.players.toMutableList()

        for (g in state.grenades) {
            val newPos = (g.pos + g.velocity * dt).clampToMap(state.mapWidth, state.mapHeight)
            val newVel = g.velocity * 0.95f.pow(dt * 60)
            val t = g.timer + dt
            if (t < g.fuseTime) { remaining.add(g.copy(pos = newPos, velocity = newVel, timer = t)); continue }
            when (g.grenadeType) {
                GrenadeType.NORMAL   -> newExp.add(Explosion(g.pos, 150f, damage = 35f))
                GrenadeType.CLUSTER  -> {
                    newExp.add(Explosion(g.pos, 80f, damage = 20f))
                    repeat(5) { idx ->
                        val a = idx * (2 * PI.toFloat() / 5)
                        newExp.add(Explosion(g.pos + Vec2(cos(a) * 60f, sin(a) * 60f), 60f, damage = 15f))
                    }
                }
                GrenadeType.ELECTRIC -> {
                    newExp.add(Explosion(g.pos, 100f, damage = 12f, color = 0xFF8800FFL))
                    for (i in players.indices) {
                        val tp = players[i]; if (!tp.isAlive) continue
                        if (tp.pos.distanceTo(g.pos) < 100f + PLAYER_RADIUS)
                            players[i] = tp.copy(statusEffects = tp.statusEffects.copy(stunTimer = tp.statusEffects.stunTimer + 2.5f))
                    }
                }
                GrenadeType.BAND -> { newZones.add(EffectZone(nextId++, g.pos, 120f, ZoneType.SLOW_FIELD, 0f, 6f, 0xFF00FFFFL, 0.25f)); newExp.add(Explosion(g.pos, 120f, damage = 0f, color = 0xFF00FFFFL)) }
                GrenadeType.SMOKE -> newZones.add(EffectZone(nextId++, g.pos, 130f, ZoneType.SMOKE, 0f, 8f, 0xFF888888L, 0.7f))
                GrenadeType.FLASH -> {
                    for (i in players.indices) {
                        val tp = players[i]; if (!tp.isAlive) continue
                        if (tp.pos.distanceTo(g.pos) < 200f + PLAYER_RADIUS)
                            players[i] = tp.copy(statusEffects = tp.statusEffects.copy(blindTimer = tp.statusEffects.blindTimer + 3f))
                    }
                    newExp.add(Explosion(g.pos, 200f, damage = 0f, color = 0xFFFFFFFFL))
                }
                GrenadeType.MEDKIT -> {}
            }
        }
        return state.copy(grenades = remaining, explosions = state.explosions + newExp,
            effectZones = state.effectZones + newZones, players = players, nextId = nextId)
    }

    // ── Effekt-Zonen ──────────────────────────────────────────────────────────
    private fun updateEffectZones(state: GameState, dt: Float): GameState {
        val remaining = mutableListOf<EffectZone>()
        val players = state.players.toMutableList()
        for (zone in state.effectZones) {
            val nz = zone.copy(timer = zone.timer + dt); if (nz.timer >= nz.duration) continue
            remaining.add(nz)
            if (zone.type == ZoneType.SLOW_FIELD) {
                for (i in players.indices) {
                    val tp = players[i]; if (!tp.isAlive) continue
                    if (tp.pos.distanceTo(zone.pos) < zone.radius + PLAYER_RADIUS)
                        players[i] = tp.copy(statusEffects = tp.statusEffects.copy(slowTimer = 0.5f, slowFactor = 0.25f))
                }
            } else if (zone.type == ZoneType.HEAL_FIELD) {
                for (i in players.indices) {
                    val tp = players[i]; if (!tp.isAlive) continue
                    if (tp.pos.distanceTo(zone.pos) < zone.radius + PLAYER_RADIUS) {
                        val newHp = (tp.hp + 20f * dt).coerceAtMost(tp.maxHp)
                        players[i] = tp.copy(hp = newHp)
                    }
                }
            }
        }
        return state.copy(effectZones = remaining, players = players)
    }

    // ── Melee ─────────────────────────────────────────────────────────────────
    private fun updateMeleeSwings(state: GameState, dt: Float): GameState {
        val remaining = mutableListOf<MeleeSwing>()
        var players = state.players
        for (swing in state.meleeSwings) {
            var currentSwing = swing
            val tipPos = currentSwing.pos + currentSwing.direction * currentSwing.range
            val newlyHit = mutableSetOf<Int>()
            
            players = players.map { t ->
                if (!t.isAlive || t.isSpawning || t.id == currentSwing.ownerId || currentSwing.hitPlayerIds.contains(t.id)) return@map t
                
                if (t.pos.distanceTo(tipPos) < PLAYER_RADIUS * 2.5f) {
                    newlyHit.add(t.id)
                    val newHp = t.hp - currentSwing.damage
                    val kbDir = (t.pos - currentSwing.pos).normalized()
                    t.copy(
                        hp = newHp.coerceAtLeast(0f),
                        isAlive = newHp > 0f,
                        velocity = t.velocity + kbDir * currentSwing.knockback,
                        lastDamagedBy = currentSwing.ownerId
                    )
                } else t
            }
            
            val ns = currentSwing.copy(
                timer = currentSwing.timer - dt,
                hitPlayerIds = currentSwing.hitPlayerIds + newlyHit
            )
            if (ns.timer > 0f) remaining.add(ns)
        }
        return state.copy(meleeSwings = remaining, players = players)
    }

    // ── Kampfzone ─────────────────────────────────────────────────────────────
    private fun updateBattleZone(state: GameState, dt: Float): GameState {
        val bz = state.battleZone
        return state.copy(battleZone = bz.copy(currentRadius = (bz.currentRadius - bz.shrinkRate * dt).coerceAtLeast(bz.targetRadius)))
    }
    private fun checkZoneDamage(state: GameState, dt: Float): GameState {
        val bz = state.battleZone; val center = Vec2(bz.centerX, bz.centerY)
        return state.copy(players = state.players.map { p ->
            if (!p.isAlive || p.isSpawning) return@map p
            if (p.pos.distanceTo(center) > bz.currentRadius) {
                val hp = (p.hp - bz.damagePerSec * dt).coerceAtLeast(0f); p.copy(hp = hp, isAlive = hp > 0f)
            } else p
        })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ── Bot-KI ────────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════════
    private fun updateBots(state: GameState, dt: Float): GameState {
        val players = state.players.toMutableList()
        val newProjectiles = mutableListOf<Projectile>()
        val newMeleeSwings = mutableListOf<MeleeSwing>()
        var nextId = state.nextId

        val updatedGroundItems = state.groundItems.toMutableList()

        for (i in players.indices) {
            var bot = players[i]
            if (bot.isLocalPlayer || !bot.isAlive || !bot.isBot) continue
            if (bot.isSpawning) {
                // Während Spawn: in Streurichtung laufen
                val dir = Vec2(cos(bot.spreadAngle), sin(bot.spreadAngle))
                val newPos = (bot.pos + dir * PLAYER_SPEED * 0.4f * dt).clampToMap(state.mapWidth, state.mapHeight)
                bot = bot.copy(pos = resolveObstacleCollision(newPos, state.obstacles, PLAYER_RADIUS), rotation = atan2(dir.y, dir.x))
                players[i] = bot; continue
            }

            // Status ticken
            val se = bot.statusEffects.copy(
                stunTimer    = (bot.statusEffects.stunTimer    - dt).coerceAtLeast(0f),
                slowTimer    = (bot.statusEffects.slowTimer    - dt).coerceAtLeast(0f),
                dashCooldown = (bot.statusEffects.dashCooldown - dt).coerceAtLeast(0f)
            )
            bot = bot.copy(statusEffects = se, wanderTimer = (bot.wanderTimer - dt).coerceAtLeast(0f))
            if (se.stunTimer > 0f) { players[i] = bot; continue }

            // ── Separation: weg von anderen Bots ──────────────────────────────
            var separation = Vec2(0f, 0f)
            for (other in players) {
                if (other.id == bot.id || !other.isAlive || other.isLocalPlayer) continue
                val diff = bot.pos - other.pos
                val d = diff.length()
                if (d < 120f && d > 0.1f) separation = separation + diff.normalized() * ((120f - d) / 120f * 200f)
            }

            // ── Tarnrüstungs-Unsichtbarkeit: Bot "sieht" ihn nur unter 80px ──
            fun canSeePlayer(target: Player): Boolean {
                if (!target.isAlive || target.isSpawning) return false
                return if (target.statusEffects.invisibleTimer > 0f)
                    target.pos.distanceTo(bot.pos) < 80f   // sehr nah: doch sichtbar
                else true
            }

            // ── Ohne Schusswaffe: nearest Weapon-Item ansteuern ───────────────
            val hasRanged = bot.inventory.gunSlots.any { it != null }
            if (!hasRanged) {
                val nearestWeaponItem = updatedGroundItems
                    .filterIsInstance<GroundItem.WeaponItem>()
                    .filter { !it.weaponType.isMelee }
                    .minByOrNull { it.pos.distanceTo(bot.pos) }

                if (nearestWeaponItem != null) {
                    val toItem = nearestWeaponItem.pos - bot.pos
                    if (toItem.length() < PLAYER_RADIUS + 50f) {
                        // aufheben
                        bot = bot.copy(inventory = bot.inventory.addWeapon(nearestWeaponItem.weaponType).copy(selectedSlotIndex = 1))
                        updatedGroundItems.removeAll { it.id == nearestWeaponItem.id }
                    } else {
                        val dir = (toItem + separation).normalized()
                        val newPos = (bot.pos + dir * PLAYER_SPEED * dt).clampToMap(state.mapWidth, state.mapHeight)
                        bot = bot.copy(pos = resolveObstacleCollision(newPos, state.obstacles, PLAYER_RADIUS), rotation = atan2(dir.y, dir.x))
                    }
                    players[i] = bot; continue
                }
            }

            // ── Nächsten sichtbaren Feind finden ──────────────────────────────
            val target = players
                .filter { it.id != bot.id && canSeePlayer(it) }
                .minByOrNull { it.pos.distanceTo(bot.pos) }

            // ── Keine Ziel sichtbar: wandern ──────────────────────────────────
            if (target == null) {
                // Richtung ggf. wechseln
                var wanderAngle = bot.wanderAngle
                if (bot.wanderTimer <= 0f) {
                    wanderAngle = bot.wanderAngle + (Random.nextFloat() - 0.5f) * PI.toFloat()
                    bot = bot.copy(wanderTimer = 3f + Random.nextFloat() * 2f)
                }
                var avoidance = Vec2(0f, 0f)
                for (obs in state.obstacles) {
                    val cx = bot.pos.x.coerceIn(obs.pos.x - 20f, obs.pos.x + obs.width + 20f)
                    val cy = bot.pos.y.coerceIn(obs.pos.y - 20f, obs.pos.y + obs.height + 20f)
                    val toWall = bot.pos - Vec2(cx, cy)
                    val d = toWall.length()
                    if (d < 80f && d > 0.1f) avoidance = avoidance + toWall.normalized() * ((80f - d) / 80f * 150f)
                }
                
                val dir = (Vec2(cos(wanderAngle), sin(wanderAngle)) + separation * 0.5f + avoidance * 0.5f).normalized()
                val newPos = (bot.pos + dir * PLAYER_SPEED * 0.7f * dt).clampToMap(state.mapWidth, state.mapHeight)
                val resolved = resolveObstacleCollision(newPos, state.obstacles, PLAYER_RADIUS)
                // Bei Hindernis-Kollision Winkel wechseln
                val newAngle = if (resolved.distanceTo(bot.pos) < 2f && avoidance.length() < 1f) wanderAngle + PI.toFloat() / 2 else wanderAngle
                bot = bot.copy(pos = resolved, wanderAngle = newAngle, rotation = atan2(dir.y, dir.x))
                players[i] = bot; continue
            }

            // ── Ziel gefunden ─────────────────────────────────────────────────
            val toTarget = target.pos - bot.pos
            val dist = toTarget.length()
            val targetDir = toTarget.normalized()
            bot = bot.copy(rotation = atan2(targetDir.y, targetDir.x))

            val activeWeapon = bot.inventory.activeWeapon
            val weaponRange = activeWeapon?.range ?: 80f
            val hpFrac = bot.hp / bot.maxHp

            // ── Zwischendurch Verstecken / Taktisch pushen ────────────────────
            var movementHandled = false
            if (bot.wanderTimer > 0f) {
                val cover = findCoverPosition(bot.pos, target.pos, state.obstacles)
                if (cover != null && cover.distanceTo(bot.pos) > 10f) {
                    val toCover = (cover - bot.pos + separation).normalized()
                    val newPos = (bot.pos + toCover * PLAYER_SPEED * dt).clampToMap(state.mapWidth, state.mapHeight)
                    bot = bot.copy(pos = resolveObstacleCollision(newPos, state.obstacles, PLAYER_RADIUS))
                    movementHandled = true
                }
            } else {
                // Ab und zu für ~2 Sekunden in Deckung huschen, ansonsten stürmen
                if (Random.nextFloat() < 0.007f) {
                    bot = bot.copy(wanderTimer = 1.5f + Random.nextFloat() * 1.5f)
                }
            }

            // ── Angreifen ─────────────────────────────────────────────────────
            if (dist < weaponRange * 0.85f && bot.fireCooldown <= 0f) {
                if (activeWeapon?.isMelee == true) {
                    val isLeft = !bot.lastMeleeLeft
                    newMeleeSwings.add(MeleeSwing(bot.id, activeWeapon, isLeft, bot.pos, targetDir, activeWeapon.range, activeWeapon.damage, activeWeapon.knockback))
                    bot = bot.copy(fireCooldown = 1f / activeWeapon.fireRate, lastMeleeLeft = isLeft)
                } else if (activeWeapon != null) {
                    newProjectiles.add(Projectile(
                        id = nextId++, ownerId = bot.id,
                        pos = bot.pos + targetDir * (PLAYER_RADIUS + activeWeapon.bulletRadius + 2f),
                        velocity = targetDir * activeWeapon.bulletSpeed,
                        damage = activeWeapon.damage, radius = activeWeapon.bulletRadius, color = activeWeapon.color,
                        lifeTime = activeWeapon.range / activeWeapon.bulletSpeed,
                        maxLifeTime = activeWeapon.range / activeWeapon.bulletSpeed,
                        isExplosive = activeWeapon == WeaponType.ROCKET_LAUNCHER,
                        explosionRadius = if (activeWeapon == WeaponType.ROCKET_LAUNCHER) 150f else 0f
                    ))
                    bot = bot.copy(fireCooldown = 1f / activeWeapon.fireRate)
                }
            }
            
            // ── Stürmen (falls nicht gerade zum Versteck gelaufen) ─────────────
            if (!movementHandled && dist > weaponRange * 0.4f) {
                var avoidance = Vec2(0f, 0f)
                for (obs in state.obstacles) {
                    val cx = bot.pos.x.coerceIn(obs.pos.x - 20f, obs.pos.x + obs.width + 20f)
                    val cy = bot.pos.y.coerceIn(obs.pos.y - 20f, obs.pos.y + obs.height + 20f)
                    val toWall = bot.pos - Vec2(cx, cy)
                    val d = toWall.length()
                    if (d < 80f && d > 0.1f) avoidance = avoidance + toWall.normalized() * ((80f - d) / 80f * 150f)
                }
                
                val approach = (targetDir + separation * 0.4f + avoidance * 0.5f).normalized()
                val speedMod = if (se.slowTimer > 0f) se.slowFactor else 1f
                val newPos = (bot.pos + approach * PLAYER_SPEED * speedMod * dt).clampToMap(state.mapWidth, state.mapHeight)
                bot = bot.copy(pos = resolveObstacleCollision(newPos, state.obstacles, PLAYER_RADIUS))
            }
            players[i] = bot
        }

        return state.copy(players = players, projectiles = state.projectiles + newProjectiles,
            meleeSwings = state.meleeSwings + newMeleeSwings, nextId = nextId, groundItems = updatedGroundItems)
    }

    /** Findet eine Position hinter einem Hindernis, aus der Richtung des Angreifers gesehen */
    private fun findCoverPosition(myPos: Vec2, threatPos: Vec2, obstacles: List<Obstacle>): Vec2? {
        val toThreat = (threatPos - myPos).normalized()
        var bestCover: Vec2? = null
        var bestScore = Float.MAX_VALUE

        for (obs in obstacles) {
            val obCenter = Vec2(obs.pos.x + obs.width / 2, obs.pos.y + obs.height / 2)
            val distToObs = obCenter.distanceTo(myPos)
            if (distToObs > 600f) continue

            // Punk hinter Hindernis (von Bedrohung aus gesehen)
            val awayFromThreat = (obCenter - threatPos).normalized()
            val coverPoint = obCenter + awayFromThreat * (obs.width.coerceAtLeast(obs.height) * 0.7f + PLAYER_RADIUS + 5f)

            val score = coverPoint.distanceTo(myPos)
            if (score < bestScore) { bestScore = score; bestCover = coverPoint }
        }
        return bestCover
    }

    // ── Schießen / Granate per LMB ────────────────────────────────────────────
    fun shoot(state: GameState, playerId: Int): GameState {
        val player = state.players.firstOrNull { it.id == playerId } ?: return state
        if (!player.isAlive || player.isSpawning || player.fireCooldown > 0f) return state

        val inv = player.inventory
        if (inv.selectedSlotIndex in 4..5) return throwGrenadeToMouse(state, playerId)

        val weapon = inv.activeWeapon ?: return state
        var newState = state; var nextId = state.nextId

        if (weapon.isMelee) {
            val dir = Vec2(cos(player.rotation), sin(player.rotation))
            val isLeft = !player.lastMeleeLeft
            newState = newState.copy(
                meleeSwings = newState.meleeSwings + MeleeSwing(playerId, weapon, isLeft, player.pos, dir, weapon.range, weapon.damage, weapon.knockback),
                players = newState.players.map { if (it.id == playerId) it.copy(lastMeleeLeft = isLeft) else it }
            )
        } else {
            val spreadCount = if (weapon == WeaponType.FLAMETHROWER) 5 else if (weapon == WeaponType.SHOTGUN) 8 else 1
            val spread = if (weapon == WeaponType.FLAMETHROWER) 0.3f else if (weapon == WeaponType.SHOTGUN) 0.5f else 0.02f
            repeat(spreadCount) { s ->
                val angle = player.rotation + (s - spreadCount / 2f) * spread / spreadCount.toFloat().coerceAtLeast(1f)
                val bDir = Vec2(cos(angle), sin(angle))
                newState = newState.copy(projectiles = newState.projectiles + Projectile(
                    id = nextId++, ownerId = playerId,
                    pos = player.pos + bDir * (PLAYER_RADIUS + weapon.bulletRadius + 2f),
                    velocity = bDir * weapon.bulletSpeed, damage = weapon.damage, radius = weapon.bulletRadius, color = weapon.color,
                    lifeTime = weapon.range / weapon.bulletSpeed, maxLifeTime = weapon.range / weapon.bulletSpeed,
                    isExplosive = weapon == WeaponType.ROCKET_LAUNCHER, explosionRadius = if (weapon == WeaponType.ROCKET_LAUNCHER) 150f else 0f
                ))
            }
        }
        val cd = 1f / weapon.fireRate
        return newState.copy(players = newState.players.map { if (it.id == playerId) it.copy(fireCooldown = cd) else it }, nextId = nextId)
    }

    fun throwGrenadeToMouse(state: GameState, playerId: Int): GameState {
        val player = state.players.firstOrNull { it.id == playerId } ?: return state
        if (!player.isAlive || player.isSpawning) return state
        val inv = player.inventory; val grenadeType = inv.activeGrenade ?: return state
        
        val idx = inv.selectedSlotIndex - 4
        val newGrenades = inv.grenadeSlots.toMutableList(); if (idx in newGrenades.indices) newGrenades[idx] = null
        val newInv = inv.copy(grenadeSlots = newGrenades)
        
        if (grenadeType == GrenadeType.MEDKIT) {
            val se = player.statusEffects
            return state.copy(
                players = state.players.map { 
                    if (it.id == playerId) it.copy(inventory = newInv, fireCooldown = 1f, statusEffects = se.copy(healRemaining = se.healRemaining + 40f)) 
                    else it 
                }
            )
        }
        
        val dir = Vec2(cos(player.rotation), sin(player.rotation))
        val grenade = ThrownGrenade(state.nextId, playerId, grenadeType, player.pos + dir * (PLAYER_RADIUS + 10f), dir * 350f, 0f)
        return state.copy(
            grenades = state.grenades + grenade, nextId = state.nextId + 1,
            players = state.players.map { if (it.id == playerId) it.copy(inventory = newInv, fireCooldown = 1f) else it }
        )
    }

    fun pickupNearby(state: GameState, playerId: Int): GameState {
        val player = state.players.firstOrNull { it.id == playerId } ?: return state
        if (!player.isAlive) return state
        val item = state.groundItems.filter { it.pos.distanceTo(player.pos) < PLAYER_RADIUS + 50f }.minByOrNull { it.pos.distanceTo(player.pos) } ?: return state
        
        var droppedItem: GroundItem? = null
        var idCounter = state.nextId
        val dropPos = player.pos + Vec2(Random.nextFloat() * 40f - 20f, Random.nextFloat() * 40f - 20f)
        
        val newInv = when (item) {
            is GroundItem.WeaponItem  -> {
                if (item.weaponType.isMelee) {
                    val oldW = player.inventory.meleeSlot
                    if (oldW != WeaponType.FISTS && oldW != null) {
                        droppedItem = GroundItem.WeaponItem(idCounter++, dropPos, oldW, Rarity.COMMON)
                    }
                    player.inventory.copy(meleeSlot = item.weaponType)
                } else {
                    val newGuns = player.inventory.gunSlots.toMutableList()
                    val freeIdx = newGuns.indexOfFirst { it == null }
                    if (freeIdx >= 0) {
                        newGuns[freeIdx] = item.weaponType
                    } else {
                        val swapIdx = if (player.inventory.selectedSlotIndex in 1..3) player.inventory.selectedSlotIndex - 1 else 0
                        droppedItem = GroundItem.WeaponItem(idCounter++, dropPos, newGuns[swapIdx]!!, Rarity.COMMON)
                        newGuns[swapIdx] = item.weaponType
                    }
                    player.inventory.copy(gunSlots = newGuns)
                }
            }
            is GroundItem.GrenadeItem -> {
                val newGrenades = player.inventory.grenadeSlots.toMutableList()
                val freeIdx = newGrenades.indexOfFirst { it == null }
                if (freeIdx >= 0) {
                    newGrenades[freeIdx] = item.grenadeType
                } else {
                    val swapIdx = if (player.inventory.selectedSlotIndex in 4..5) player.inventory.selectedSlotIndex - 4 else 0
                    droppedItem = GroundItem.GrenadeItem(idCounter++, dropPos, newGrenades[swapIdx]!!, Rarity.COMMON)
                    newGrenades[swapIdx] = item.grenadeType
                }
                player.inventory.copy(grenadeSlots = newGrenades)
            }
            is GroundItem.ArmorItem   -> {
                val oldArmor = player.inventory.armorSlot
                if (oldArmor != null) {
                    droppedItem = GroundItem.ArmorItem(idCounter++, dropPos, oldArmor, Rarity.COMMON)
                }
                player.inventory.copy(armorSlot = item.armorType)
            }
        }
        
        val newItems = state.groundItems.filter { it.id != item.id }.toMutableList()
        if (droppedItem != null) newItems.add(droppedItem)
        
        return state.copy(players = state.players.map { if (it.id == playerId) it.copy(inventory = newInv) else it },
            groundItems = newItems, nextId = idCounter)
    }

    fun dash(state: GameState, playerId: Int): GameState {
        val player = state.players.firstOrNull { it.id == playerId } ?: return state
        if (!player.isAlive || player.isSpawning || player.inventory.armorSlot != ArmorType.AGILITY) return state
        val se = player.statusEffects; if (se.dashCooldown > 0f) return state
        val dir = Vec2(cos(player.rotation), sin(player.rotation))
        val newSe = se.copy(dashTimer = 0.25f, dashVelocity = dir * 500f, dashCooldown = 5f)
        return state.copy(players = state.players.map { if (it.id == playerId) it.copy(statusEffects = newSe) else it })
    }

    fun activateStealth(state: GameState, playerId: Int): GameState {
        val player = state.players.firstOrNull { it.id == playerId } ?: return state
        if (!player.isAlive || player.isSpawning || player.inventory.armorSlot != ArmorType.STEALTH) return state
        val se = player.statusEffects; if (se.dashCooldown > 0f) return state
        val newSe = se.copy(invisibleTimer = 5f, dashCooldown = 15f)
        return state.copy(players = state.players.map { if (it.id == playerId) it.copy(statusEffects = newSe) else it })
    }

    fun scrollSlot(state: GameState, playerId: Int, up: Boolean): GameState {
        return state.copy(players = state.players.map { p ->
            if (p.id != playerId) p else p.copy(inventory = if (up) p.inventory.scrollNext() else p.inventory.scrollPrev())
        })
    }

    fun resolveObstacleCollision(pos: Vec2, obstacles: List<Obstacle>, radius: Float): Vec2 {
        var result = pos
        for (obs in obstacles) {
            if (!obs.intersectsCircle(result, radius)) continue
            val cx = result.x.coerceIn(obs.pos.x, obs.pos.x + obs.width)
            val cy = result.y.coerceIn(obs.pos.y, obs.pos.y + obs.height)
            val diff = result - Vec2(cx, cy); val d = diff.length()
            result = if (d < 0.0001f) result + Vec2(radius + 1f, 0f) else Vec2(cx, cy) + diff.normalized() * (radius + 1f)
        }
        return result
    }
}

private fun Float.pow(exp: Float): Float = this.toDouble().pow(exp.toDouble()).toFloat()
