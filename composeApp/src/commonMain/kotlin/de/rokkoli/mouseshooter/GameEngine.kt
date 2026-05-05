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
        val cols = (mapW / 750f).toInt().coerceAtLeast(8)
        val rows = (mapH / 750f).toInt().coerceAtLeast(8)
        val cellW = (mapW - 1000f) / cols
        val cellH = (mapH - 1000f) / rows
        val startX = 500f
        val startY = 500f

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

        // ─── Map-Begrenzung (Wände am Rand) ──────────────────────────────────
        val wallThickness = 150f
        obstacles.add(Obstacle(Vec2(-wallThickness, -wallThickness), mapW + 2 * wallThickness, wallThickness, 0xFF222222L)) // Oben
        obstacles.add(Obstacle(Vec2(-wallThickness, mapH), mapW + 2 * wallThickness, wallThickness, 0xFF222222L))           // Unten
        obstacles.add(Obstacle(Vec2(-wallThickness, 0f), wallThickness, mapH, 0xFF222222L))                               // Links
        obstacles.add(Obstacle(Vec2(mapW, 0f), wallThickness, mapH, 0xFF222222L))                                          // Rechts

        val maxDist = sqrt(mapW * mapW + mapH * mapH) / 2f

        // ─── Loot-Pool System ──────────────────────────────────────────────
        
        // 1. Garantierte Mindestmenge (genug für ca. 10 Spieler)
        val guaranteedWeapons = 15
        val guaranteedAmmo = 20
        val guaranteedGrenades = 10
        val guaranteedMedkits = 10
        val guaranteedArmor = 8

        fun spawnItem(type: String) {
            val angle = random.nextFloat() * 2 * PI.toFloat()
            // sqrt(random) für gleichmäßige Verteilung in der Fläche (verhindert Clustering in der Mitte)
            val baseDist = sqrt(random.nextFloat()) * (maxDist * 0.9f)
            
            // Waffen und Rüstung nicht direkt im Zentrum spawnen
            val dist = if (type == "weapon" || type == "armor") {
                MIN_WEAPON_SPAWN_DIST + (baseDist * 0.8f)
            } else {
                baseDist.coerceAtLeast(300f)
            }
            
            val pos = Vec2(center.x + cos(angle) * dist, center.y + sin(angle) * dist).clampToMap(mapW, mapH)
            val rarity = rarityFromDistance(dist, maxDist, random)
            
            when (type) {
                "weapon" -> {
                    val pool = if (rarity.ordinal >= Rarity.EPIC.ordinal)
                        listOf(WeaponType.SMG, WeaponType.FLAMETHROWER, WeaponType.ROCKET_LAUNCHER, WeaponType.SHOTGUN, WeaponType.SNIPER, WeaponType.MINIGUN)
                    else if (rarity.ordinal >= Rarity.RARE.ordinal)
                        listOf(WeaponType.SMG, WeaponType.SHOTGUN)
                    else listOf(WeaponType.PISTOL, WeaponType.KNIFE, WeaponType.LONG_KNIFE, WeaponType.SHOTGUN)
                    items.add(GroundItem.WeaponItem(idCounter++, pos, pool.random(random), rarity))
                }
                "grenade" -> items.add(GroundItem.GrenadeItem(idCounter++, pos, GrenadeType.entries.filter { it != GrenadeType.MEDKIT }.random(random), rarity))
                "medkit" -> items.add(GroundItem.GrenadeItem(idCounter++, pos, GrenadeType.MEDKIT, rarity))
                "armor" -> items.add(GroundItem.ArmorItem(idCounter++, pos, ArmorType.entries.random(random), rarity))
                "ammo" -> {
                    val ammoType = AmmoType.entries.random(random)
                    val amt = when(ammoType) {
                        AmmoType.LIGHT -> 30
                        AmmoType.HEAVY -> 10
                        AmmoType.SHELLS -> 8
                        AmmoType.ROCKETS -> 2
                        AmmoType.FUEL -> 100
                    }
                    items.add(GroundItem.AmmoItem(idCounter++, pos, ammoType, amt, rarity))
                }
            }
        }

        // Spawne Garantierte Items
        repeat(guaranteedWeapons) { spawnItem("weapon") }
        repeat(guaranteedAmmo) { spawnItem("ammo") }
        repeat(guaranteedGrenades) { spawnItem("grenade") }
        repeat(guaranteedMedkits) { spawnItem("medkit") }
        repeat(guaranteedArmor) { spawnItem("armor") }

        // 2. Der Rest wird "ausgelost" (Zusätzlicher Lootpool)
        // Skaliere die Item-Anzahl mit der Map-Größe (ca. 1 Item pro 1.000.000 pixel^2)
        val mapArea = mapW * mapH
        val baseAdditional = (mapArea / 1000000f).toInt().coerceIn(50, 400)
        val additionalItems = baseAdditional + random.nextInt(baseAdditional / 2)
        repeat(additionalItems) {
            val r = random.nextFloat()
            val type = when {
                r < 0.35f -> "weapon"  // 35% Chance Waffe
                r < 0.70f -> "ammo"    // 35% Chance Munition
                r < 0.82f -> "grenade" // 12% Chance Granate
                r < 0.92f -> "medkit"  // 10% Chance Medkit
                else      -> "armor"   // 8% Chance Rüstung
            }
            spawnItem(type)
        }

        // Nahkampf-Waffen (wie bisher als kleiner Bonus im Zentrum)
        for (i in 0..2) {
            val a = i * (2 * PI.toFloat() / 3)
            val d = 700f + random.nextFloat() * 400f
            val pos = Vec2(center.x + cos(a) * d, center.y + sin(a) * d).clampToMap(mapW, mapH)
            items.add(GroundItem.WeaponItem(idCounter++, pos,
                listOf(WeaponType.KNIFE, WeaponType.LONG_KNIFE, WeaponType.BOXING_GLOVES).random(random), Rarity.COMMON))
        }

        return Pair(obstacles, items)
    }
}

fun Vec2.clampToMap(w: Float, h: Float) = Vec2(x.coerceIn(50f, w - 50f), y.coerceIn(50f, h - 50f))

// ─── Initial-State ────────────────────────────────────────────────────────────
fun createInitialState(): GameState {
    val mapW = 15000f
    val mapH = 15000f
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
        battleZone = BattleZone(
            currentRadius = 12000f, 
            targetRadius = 12000f, 
            startRadius = 12000f, 
            centerX = center.x, 
            centerY = center.y,
            damagePerSec = 4f
        ),
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
                is GroundItem.AmmoItem    -> item.copy(glowPhase = (item.glowPhase + dt * 2f) % (2 * PI.toFloat()))
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
                upd.inventory.gunSlots.forEachIndexed { i, w ->
                    if (w != null) {
                        nextItems.add(GroundItem.WeaponItem(nId++, upd.pos + Vec2(Random.nextFloat()*40-20, Random.nextFloat()*40-20), w, upd.inventory.gunRarities[i]))
                    }
                }
                if (upd.inventory.meleeSlot != WeaponType.FISTS && upd.inventory.meleeSlot != null) {
                    nextItems.add(GroundItem.WeaponItem(nId++, upd.pos + Vec2(Random.nextFloat()*40-20, Random.nextFloat()*40-20), upd.inventory.meleeSlot!!, upd.inventory.meleeRarity))
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

        // ── Kamera-Steuerung (Spectating) ─────────────────────────────────────
        val localP = s.players.firstOrNull { it.isLocalPlayer }
        val cameraTarget = if (localP?.isAlive == true) {
            localP
        } else {
            // Wenn der lokale Spieler tot ist, spectate jemanden anders
            var specTarget = s.players.firstOrNull { it.id == s.spectatedPlayerId && it.isAlive }
            if (specTarget == null) {
                // Suche neuen lebenden Spieler zum Zuschauen
                specTarget = s.players.firstOrNull { it.isAlive && it.id != localP?.id }
                if (specTarget != null) {
                    s = s.copy(spectatedPlayerId = specTarget.id)
                }
            }
            specTarget ?: localP // Fallback zum eigenen Todesort
        }

        if (cameraTarget != null) {
            s = s.copy(cameraX = cameraTarget.pos.x, cameraY = cameraTarget.pos.y)
        }

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
            var newReloadTimer = (p.reloadTimer - dt).coerceAtLeast(0f)
            var newIsReloading = p.isReloading
            var newInv = p.inventory

            // Nachladen abschließen
            if (p.isReloading && newReloadTimer <= 0f) {
                newIsReloading = false
                val activeWeapon = p.inventory.activeWeapon
                if (activeWeapon != null && activeWeapon.ammoType != null) {
                    val slotIdx = p.inventory.selectedSlotIndex - 1
                    val rarity = p.inventory.gunRarities.getOrNull(slotIdx) ?: Rarity.COMMON
                    val ammoType = activeWeapon.ammoType
                    val reserve = p.inventory.reserveAmmo[ammoType] ?: 0
                    
                    val actualClipSize = if (activeWeapon == WeaponType.SHOTGUN) {
                        if (rarity.ordinal >= Rarity.EPIC.ordinal) 2 else 1
                    } else activeWeapon.clipSize
                    
                    val current = p.inventory.clipAmmo.getOrNull(slotIdx) ?: 0
                    val needed = actualClipSize - current
                    val toReload = reserve.coerceAtMost(needed)
                    
                    if (toReload > 0) {
                        val newReserves = p.inventory.reserveAmmo.toMutableMap()
                        newReserves[ammoType] = reserve - toReload
                        val newClips = p.inventory.clipAmmo.toMutableList()
                        if (slotIdx in 0..2) {
                            newClips[slotIdx] += toReload
                        }
                        newInv = p.inventory.copy(reserveAmmo = newReserves, clipAmmo = newClips)
                    }
                }
            }

            p = p.copy(
                statusEffects = newSe,
                fireCooldown = (p.fireCooldown - dt).coerceAtLeast(0f),
                reloadTimer = newReloadTimer,
                isReloading = newIsReloading,
                inventory = newInv
            )

            if (!p.isLocalPlayer && !p.isBot) return@map p // Bots brauchen Bewegung, aber kein Maus-Input
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
            val oldPos = proj.pos
            val newPos = (oldPos + proj.velocity * dt)
            val p = proj.copy(pos = newPos, lifeTime = proj.lifeTime - dt)
            
            fun addExp(at: Vec2) { if (p.isExplosive) newExp.add(Explosion(at, p.explosionRadius, damage = p.damage)) }
            
            if (p.lifeTime <= 0f) { addExp(newPos); continue }
            
            // Kollision mit Hindernissen auf dem Pfad
            val hitObstacle = state.obstacles.firstOrNull { it.intersectsSegment(oldPos, newPos, p.radius) }
            if (hitObstacle != null) {
                addExp(newPos) // Einfachheitshalber neue Position für Explosion
                continue
            }
            
            if (newPos.x < 0 || newPos.x > state.mapWidth || newPos.y < 0 || newPos.y > state.mapHeight) { 
                addExp(newPos); continue 
            }
            
            var hit = false
            for (i in players.indices) {
                val t = players[i]; if (!t.isAlive || t.isSpawning || t.id == p.ownerId) continue
                // Prüfe ob Pfad den Spieler schneidet
                if (intersectsSegmentCircle(oldPos, newPos, t.pos, PLAYER_RADIUS + p.radius)) {
                    val newHp = t.hp - p.damage
                    players[i] = t.copy(hp = newHp.coerceAtLeast(0f), isAlive = newHp > 0f, lastDamagedBy = p.ownerId)
                    addExp(newPos); hit = true; break
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
        var bz = state.battleZone
        var newPhaseTimer = bz.phaseTimer - dt
        
        if (newPhaseTimer <= 0) {
            if (!bz.isShrinking) {
                // Von Warten auf Schrumpfen wechseln
                val nextTarget = if (bz.phase == 0) (bz.currentRadius * 0.6f) else (bz.currentRadius * 0.5f)
                bz = bz.copy(
                    isShrinking = true, 
                    phaseTimer = bz.shrinkDuration, 
                    startRadius = bz.currentRadius,
                    targetRadius = nextTarget.coerceAtLeast(150f)
                )
            } else {
                // Von Schrumpfen auf Warten wechseln (Nächste Phase vorbereiten)
                bz = bz.copy(
                    isShrinking = false, 
                    phaseTimer = bz.waitDuration, 
                    currentRadius = bz.targetRadius,
                    phase = bz.phase + 1
                )
            }
        } else {
            var newCurrentRadius = bz.currentRadius
            if (bz.isShrinking) {
                // Interpoliere Radius während des Schrumpfens
                val progress = 1f - (newPhaseTimer / bz.shrinkDuration)
                newCurrentRadius = bz.startRadius + (bz.targetRadius - bz.startRadius) * progress.coerceIn(0f, 1f)
            }
            bz = bz.copy(phaseTimer = newPhaseTimer, currentRadius = newCurrentRadius)
        }
        
        return state.copy(battleZone = bz)
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
                        bot = bot.copy(inventory = bot.inventory.addWeapon(nearestWeaponItem.weaponType, nearestWeaponItem.rarity).copy(selectedSlotIndex = 1))
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
            if (dist < weaponRange * 0.85f && bot.fireCooldown <= 0f && !bot.isReloading) {
                if (activeWeapon?.isMelee == true) {
                    val isLeft = !bot.lastMeleeLeft
                    newMeleeSwings.add(MeleeSwing(bot.id, activeWeapon, isLeft, bot.pos, targetDir, activeWeapon.range, activeWeapon.damage, activeWeapon.knockback))
                    bot = bot.copy(fireCooldown = 1f / activeWeapon.fireRate, lastMeleeLeft = isLeft)
                } else if (activeWeapon != null) {
                    val clipIdx = bot.inventory.selectedSlotIndex - 1
                    val currentAmmo = bot.inventory.clipAmmo.getOrNull(clipIdx) ?: 0
                    if (currentAmmo > 0) {
                        // Munition abziehen
                        val newClips = bot.inventory.clipAmmo.toMutableList()
                        newClips[clipIdx] = currentAmmo - 1
                        bot = bot.copy(inventory = bot.inventory.copy(clipAmmo = newClips))

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
                    } else {
                        val slotIdx = bot.inventory.selectedSlotIndex - 1
                        val rarity = bot.inventory.gunRarities.getOrNull(slotIdx) ?: Rarity.COMMON
                        bot = bot.copy(isReloading = true, reloadTimer = activeWeapon.reloadTime * rarity.reloadMod)
                    }
                }
            }
            
            // ── Munition sammeln falls nötig ─────────────────────────────────
            if (bot.inventory.reserveAmmo.values.sum() < 20) {
                val nearestAmmo = updatedGroundItems
                    .filterIsInstance<GroundItem.AmmoItem>()
                    .minByOrNull { it.pos.distanceTo(bot.pos) }
                if (nearestAmmo != null && nearestAmmo.pos.distanceTo(bot.pos) < 500f) {
                    if (nearestAmmo.pos.distanceTo(bot.pos) < PLAYER_RADIUS + 50f) {
                        val newReserves = bot.inventory.reserveAmmo.toMutableMap()
                        newReserves[nearestAmmo.ammoType] = (newReserves[nearestAmmo.ammoType] ?: 0) + nearestAmmo.amount
                        bot = bot.copy(inventory = bot.inventory.copy(reserveAmmo = newReserves))
                        updatedGroundItems.removeAll { it.id == nearestAmmo.id }
                    } else if (!movementHandled) {
                        val toAmmo = (nearestAmmo.pos - bot.pos + separation).normalized()
                        val newPos = (bot.pos + toAmmo * PLAYER_SPEED * dt).clampToMap(state.mapWidth, state.mapHeight)
                        bot = bot.copy(pos = resolveObstacleCollision(newPos, state.obstacles, PLAYER_RADIUS), rotation = atan2(toAmmo.y, toAmmo.x))
                        movementHandled = true
                    }
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
        if (!player.isAlive || player.isSpawning || player.isReloading || player.fireCooldown > 0f) return state

        val inv = player.inventory
        if (inv.selectedSlotIndex == 6) return activateArmorAbility(state, playerId)
        if (inv.selectedSlotIndex in 4..5) return throwGrenadeToMouse(state, playerId)

        val weapon = inv.activeWeapon ?: return state
        val rarity = when (inv.selectedSlotIndex) {
            0 -> inv.meleeRarity
            in 1..3 -> inv.gunRarities.getOrNull(inv.selectedSlotIndex - 1) ?: Rarity.COMMON
            else -> Rarity.COMMON
        }

        if (!weapon.isMelee) {
            val clipIdx = inv.selectedSlotIndex - 1
            val currentAmmo = inv.clipAmmo.getOrNull(clipIdx) ?: 0
            if (currentAmmo <= 0) {
                val reserve = inv.reserveAmmo[weapon.ammoType] ?: 0
                if (reserve > 0) {
                    return state.copy(players = state.players.map {
                        if (it.id == playerId) it.copy(isReloading = true, reloadTimer = weapon.reloadTime * rarity.reloadMod) else it
                    })
                }
                return state
            }
        }

        var newState = state; var nextId = state.nextId
        val damage = weapon.damage * rarity.damageMod
        val fireRate = weapon.fireRate * rarity.fireRateMod

        if (weapon.isMelee) {
            val dir = Vec2(cos(player.rotation), sin(player.rotation))
            val isLeft = !player.lastMeleeLeft
            newState = newState.copy(
                meleeSwings = newState.meleeSwings + MeleeSwing(playerId, weapon, isLeft, player.pos, dir, weapon.range, damage, weapon.knockback),
                players = newState.players.map { if (it.id == playerId) it.copy(lastMeleeLeft = isLeft) else it }
            )
        } else {
            val clipIdx = inv.selectedSlotIndex - 1
            val newClips = inv.clipAmmo.toMutableList()
            if (clipIdx in newClips.indices) newClips[clipIdx] = (newClips[clipIdx] - 1).coerceAtLeast(0)
            
            newState = newState.copy(players = newState.players.map { 
                if (it.id == playerId) it.copy(inventory = it.inventory.copy(clipAmmo = newClips)) else it 
            })

            val spreadCount = if (weapon == WeaponType.FLAMETHROWER) 5 else if (weapon == WeaponType.SHOTGUN) 8 else 1
            val spread = if (weapon == WeaponType.FLAMETHROWER) 0.3f else if (weapon == WeaponType.SHOTGUN) 0.5f else 0.02f
            repeat(spreadCount) { s ->
                val angle = player.rotation + (s - spreadCount / 2f) * spread / spreadCount.toFloat().coerceAtLeast(1f)
                val bDir = Vec2(cos(angle), sin(angle))
                newState = newState.copy(projectiles = newState.projectiles + Projectile(
                    id = nextId++, ownerId = playerId,
                    pos = player.pos + bDir * (PLAYER_RADIUS + weapon.bulletRadius + 2f),
                    velocity = bDir * weapon.bulletSpeed, damage = damage, radius = weapon.bulletRadius, color = weapon.color,
                    lifeTime = weapon.range / weapon.bulletSpeed, maxLifeTime = weapon.range / weapon.bulletSpeed,
                    isExplosive = weapon == WeaponType.ROCKET_LAUNCHER, explosionRadius = if (weapon == WeaponType.ROCKET_LAUNCHER) 150f else 0f
                ))
            }
        }
        
        val cd = 1f / fireRate
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
                        droppedItem = GroundItem.WeaponItem(idCounter++, dropPos, oldW, player.inventory.meleeRarity)
                    }
                } else {
                    val swapIdx = if (player.inventory.selectedSlotIndex in 1..3) player.inventory.selectedSlotIndex - 1 else 0
                    if (player.inventory.gunSlots.all { it != null }) {
                        droppedItem = GroundItem.WeaponItem(idCounter++, dropPos, player.inventory.gunSlots[swapIdx]!!, player.inventory.gunRarities[swapIdx])
                    }
                }
                player.inventory.addWeapon(item.weaponType, item.rarity)
            }
            is GroundItem.GrenadeItem -> {
                if (player.inventory.grenadeSlots.all { it != null }) {
                    val swapIdx = if (player.inventory.selectedSlotIndex in 4..5) player.inventory.selectedSlotIndex - 4 else 0
                    droppedItem = GroundItem.GrenadeItem(idCounter++, dropPos, player.inventory.grenadeSlots[swapIdx]!!, player.inventory.grenadeRarities[swapIdx])
                }
                player.inventory.addGrenade(item.grenadeType, item.rarity)
            }
            is GroundItem.ArmorItem   -> {
                val oldArmor = player.inventory.armorSlot
                if (oldArmor != null) {
                    droppedItem = GroundItem.ArmorItem(idCounter++, dropPos, oldArmor, player.inventory.armorRarity ?: Rarity.COMMON)
                }
                player.inventory.addArmor(item.armorType, item.rarity)
            }
            is GroundItem.AmmoItem -> {
                val newReserves = player.inventory.reserveAmmo.toMutableMap()
                val current = newReserves[item.ammoType] ?: 0
                newReserves[item.ammoType] = current + item.amount
                player.inventory.copy(reserveAmmo = newReserves)
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

    fun activateArmorAbility(state: GameState, playerId: Int): GameState {
        val player = state.players.firstOrNull { it.id == playerId } ?: return state
        return when (player.inventory.armorSlot) {
            ArmorType.AGILITY -> dash(state, playerId)
            ArmorType.STEALTH -> activateStealth(state, playerId)
            else -> state
        }
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

    private fun intersectsSegmentCircle(p1: Vec2, p2: Vec2, center: Vec2, radius: Float): Boolean {
        val d = p2 - p1
        val f = p1 - center
        val a = d.dot(d)
        if (a < 0.0001f) return p1.distanceTo(center) < radius
        val b = 2 * f.dot(d)
        val c = f.dot(f) - radius * radius
        var discriminant = b * b - 4 * a * c
        if (discriminant < 0) return false
        discriminant = sqrt(discriminant)
        val t1 = (-b - discriminant) / (2 * a)
        val t2 = (-b + discriminant) / (2 * a)
        return (t1 in 0f..1f) || (t2 in 0f..1f) || (t1 < 0f && t2 > 1f)
    }
}

private fun Float.pow(exp: Float): Float = this.toDouble().pow(exp.toDouble()).toFloat()
