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
    fun generate(mapW: Float, mapH: Float, random: kotlin.random.Random = kotlin.random.Random): Triple<List<Obstacle>, List<GroundItem>, List<LootCrate>> {
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

        // ─── Loot-Kisten System ──────────────────────────────────────────────
        val crates = mutableListOf<LootCrate>()
        var crateIdCounter = 5000

        fun crateRarityFromDistance(dist: Float, maxDist: Float): Rarity {
            val normalizedDist = (dist / maxDist).coerceIn(0f, 1f)
            val r = random.nextFloat()
            return when {
                normalizedDist < 0.3f -> when { // Nah am Zentrum
                    r < 0.60f -> Rarity.COMMON
                    r < 0.85f -> Rarity.UNCOMMON
                    r < 0.95f -> Rarity.RARE
                    r < 0.99f -> Rarity.EPIC
                    else -> Rarity.MYTHIC // Keine Legendaries im Zentrum
                }
                normalizedDist < 0.6f -> when { // Mittlerer Bereich
                    r < 0.35f -> Rarity.COMMON
                    r < 0.60f -> Rarity.UNCOMMON
                    r < 0.82f -> Rarity.RARE
                    r < 0.93f -> Rarity.EPIC
                    r < 0.98f -> Rarity.MYTHIC
                    else -> Rarity.LEGENDARY
                }
                else -> when { // Weit außen
                    r < 0.15f -> Rarity.COMMON
                    r < 0.35f -> Rarity.UNCOMMON
                    r < 0.60f -> Rarity.RARE
                    r < 0.80f -> Rarity.EPIC
                    r < 0.93f -> Rarity.MYTHIC
                    else -> Rarity.LEGENDARY
                }
            }
        }

        // Kisten spawnen (ca. 45 Kisten für die Karte)
        val mapArea = mapW * mapH
        val crateCount = (mapArea / 800000f).toInt().coerceIn(30, 60)
        repeat(crateCount) {
            val angle = random.nextFloat() * 2 * PI.toFloat()
            val baseDist = sqrt(random.nextFloat()) * (maxDist * 0.9f)
            val dist = (MIN_WEAPON_SPAWN_DIST * 0.5f + baseDist * 0.8f).coerceAtMost(maxDist * 0.95f)
            val pos = Vec2(center.x + cos(angle) * dist, center.y + sin(angle) * dist).clampToMap(mapW, mapH)
            val rarity = crateRarityFromDistance(dist, maxDist)
            crates.add(LootCrate(id = crateIdCounter++, pos = pos, rarity = rarity))
        }

        return Triple(obstacles, items, crates)
    }

    /** Erzeugt 1-3 Items als Loot, wenn eine Kiste zerstört wird. */
    fun generateCrateLoot(crate: LootCrate, nextId: Int, random: kotlin.random.Random = kotlin.random.Random): List<GroundItem> {
        val loot = mutableListOf<GroundItem>()
        var id = nextId
        val minRarity = crate.rarity

        fun itemRarity(): Rarity {
            val r = random.nextFloat()
            val base = minRarity.ordinal
            // Chance auf höhere Seltenheit, aber nie unter der Kisten-Rarity
            val bonus = when {
                r < 0.55f -> 0    // 55% gleiche Rarity
                r < 0.80f -> 1    // 25% +1 Stufe
                r < 0.93f -> 2    // 13% +2 Stufen
                else -> 3         //  7% +3 Stufen
            }
            return Rarity.entries[(base + bonus).coerceAtMost(Rarity.entries.lastIndex)]
        }

        // 1-3 Items (gewichtet: 20% = 1, 50% = 2, 30% = 3)
        val itemCount = when {
            random.nextFloat() < 0.20f -> 1
            random.nextFloat() < 0.71f -> 2   // 50/(100-20)
            else -> 3
        }

        // Item-Typen: 0=Waffe, 1=Munition, 2=Granate, 3=Medkit, 4=Rüstung
        val typeCounts = mutableMapOf<Int, Int>()
        val offset = 25f  // Abstand zwischen Items

        repeat(itemCount) { i ->
            // Zufälligen Typ wählen, max 2 vom gleichen
            var type: Int
            var attempts = 0
            do {
                val r = random.nextFloat()
                type = when {
                    r < 0.30f -> 0  // 30% Waffe
                    r < 0.60f -> 1  // 30% Munition
                    r < 0.75f -> 2  // 15% Granate
                    r < 0.88f -> 3  // 13% Medkit
                    else -> 4       // 12% Rüstung
                }
                attempts++
            } while ((typeCounts[type] ?: 0) >= 2 && attempts < 10)

            typeCounts[type] = (typeCounts[type] ?: 0) + 1
            val itemPos = Vec2(crate.pos.x + (i - (itemCount - 1) / 2f) * offset, crate.pos.y)
            val rarity = itemRarity()

            when (type) {
                0 -> { // Waffe
                    val pool = if (rarity.ordinal >= Rarity.LEGENDARY.ordinal)
                        listOf(WeaponType.LASER_BEAM, WeaponType.LASER_SWORD, WeaponType.MINIGUN, WeaponType.SNIPER, WeaponType.ROCKET_LAUNCHER, WeaponType.DUAL_ENERGY_PISTOL, WeaponType.ENERGY_RIFLE, WeaponType.KATANA, WeaponType.CHAINSAW)
                    else if (rarity.ordinal >= Rarity.MYTHIC.ordinal)
                        listOf(WeaponType.LASER_BEAM, WeaponType.DUAL_ENERGY_PISTOL, WeaponType.MINIGUN, WeaponType.SNIPER, WeaponType.ROCKET_LAUNCHER, WeaponType.ENERGY_RIFLE, WeaponType.KATANA, WeaponType.CHAINSAW)
                    else if (rarity.ordinal >= Rarity.EPIC.ordinal)
                        listOf(WeaponType.DUAL_ENERGY_PISTOL, WeaponType.ENERGY_RIFLE, WeaponType.FLAMETHROWER, WeaponType.ROCKET_LAUNCHER, WeaponType.MINIGUN, WeaponType.SNIPER, WeaponType.CHAINSAW, WeaponType.KATANA)
                    else if (rarity.ordinal >= Rarity.RARE.ordinal)
                        listOf(WeaponType.DUAL_PISTOL, WeaponType.ENERGY_RIFLE, WeaponType.SMG, WeaponType.SHOTGUN, WeaponType.FLAMETHROWER, WeaponType.CHAINSAW, WeaponType.KATANA, WeaponType.LONG_KNIFE)
                    else if (rarity.ordinal >= Rarity.UNCOMMON.ordinal)
                        listOf(WeaponType.DUAL_PISTOL, WeaponType.SMG, WeaponType.SHOTGUN, WeaponType.LONG_KNIFE, WeaponType.PISTOL)
                    else listOf(WeaponType.PISTOL, WeaponType.KNIFE, WeaponType.BOXING_GLOVES, WeaponType.SHOTGUN)
                    
                    loot.add(GroundItem.WeaponItem(id++, itemPos, pool.random(random), rarity))
                }
                1 -> { // Munition
                    val ammoType = AmmoType.entries.random(random)
                    val amt = when(ammoType) {
                        AmmoType.LIGHT -> 30; AmmoType.HEAVY -> 10; AmmoType.SHELLS -> 8
                        AmmoType.ROCKETS -> 2; AmmoType.OIL -> 100; AmmoType.ENERGY -> 50
                    }
                    loot.add(GroundItem.AmmoItem(id++, itemPos, ammoType, amt, rarity))
                }
                2 -> { // Granate
                    loot.add(GroundItem.GrenadeItem(id++, itemPos, GrenadeType.entries.filter { it != GrenadeType.MEDKIT }.random(random), rarity))
                }
                3 -> { // Medkit
                    loot.add(GroundItem.GrenadeItem(id++, itemPos, GrenadeType.MEDKIT, rarity))
                }
                4 -> { // Rüstung
                    loot.add(GroundItem.ArmorItem(id++, itemPos, ArmorType.entries.random(random), rarity))
                }
            }
        }
        return loot
    }
}

fun Vec2.clampToMap(w: Float, h: Float) = Vec2(x.coerceIn(50f, w - 50f), y.coerceIn(50f, h - 50f))

// ─── Initial-State ────────────────────────────────────────────────────────────
fun createInitialState(): GameState {
    val mapW = 15000f
    val mapH = 15000f
    val center = Vec2(mapW / 2, mapH / 2)
    val (obstacles, items, crates) = MapGenerator.generate(mapW, mapH)

    val players = mutableListOf<Player>()

    players.add(Player(
        id = 0, pos = center, hp = 100f, isLocalPlayer = true,
        inventory = Inventory(meleeSlot = WeaponType.FISTS, gunSlots = listOf(null, null, null), selectedSlotIndex = 0),
        color = 0xFF00CCFF, isSpawning = true, spawnTimer = 3.5f
    ))

    val botColors = listOf(0xFFFF4444L, 0xFFFF8800L, 0xFFFF00AAL, 0xFF00FF88L, 0xFFFFFF00L, 0xFFAA00FFL, 0xFF00AAFFL)
    for (i in 1..BOT_COUNT) {
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
        players = players, groundItems = items, obstacles = obstacles, lootCrates = crates,
        battleZone = BattleZone(
            currentRadius = 12000f, 
            targetRadius = 7200f, 
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

fun createTestMapState(): GameState {
    val mapW = 2000f
    val mapH = 2000f
    val center = Vec2(mapW / 2, mapH / 2)
    val obstacles = listOf(
        Obstacle(pos = Vec2(center.x + 300f, center.y - 100f), width = 20f, height = 200f, color = 0xFF888899),
        Obstacle(pos = Vec2(center.x + 300f, center.y - 100f), width = 200f, height = 20f, color = 0xFF888899)
    )
    
    val players = mutableListOf<Player>()
    players.add(Player(
        id = 0, pos = center, hp = 100f, isLocalPlayer = true,
        inventory = Inventory(meleeSlot = WeaponType.FISTS, gunSlots = listOf(null, null, null), selectedSlotIndex = 0),
        color = 0xFF00CCFF, isSpawning = false
    ))

    val botColors = listOf(0xFFFF4444L, 0xFFFF8800L, 0xFFFF00AAL)
    for (i in 1..2) {
        val botPos = Vec2(center.x - 200f, center.y - 200f + i * 150f)
        players.add(Player(
            id = i, pos = botPos, hp = 100f, isLocalPlayer = false, isBot = true, isDummyBot = true,
            inventory = Inventory(meleeSlot = WeaponType.FISTS, gunSlots = listOf(null, null, null), selectedSlotIndex = 0),
            color = botColors[(i - 1) % botColors.size],
            isSpawning = false
        ))
    }
    // Shooting Dummy
    players.add(Player(
        id = 3, pos = Vec2(center.x - 200f, center.y + 100f), hp = 1000f, maxHp = 1000f, isLocalPlayer = false, isBot = true, isDummyBot = true, isShootingDummy = true,
        inventory = Inventory(meleeSlot = WeaponType.FISTS, gunSlots = listOf(WeaponType.SMG, null, null), selectedSlotIndex = 1, reserveAmmo = mutableMapOf(AmmoType.LIGHT to 9999)),
        color = 0xFFFFFFFF, isSpawning = false, spreadAngle = 0f, wanderAngle = 0f // Schießt nach rechts (0 rad)
    ))

    val crates = mutableListOf<LootCrate>()
    for (i in 0..4) {
        crates.add(LootCrate(id = 5000 + i, pos = Vec2(center.x + 200f, center.y - 200f + i * 80f), rarity = Rarity.entries[i % Rarity.entries.size]))
    }

    val items = mutableListOf<GroundItem>()
    var id = 6000
    val allWeapons = WeaponType.entries
    for (i in allWeapons.indices) {
        items.add(GroundItem.WeaponItem(id++, Vec2(center.x - 400f + (i % 10) * 80f, center.y + 400f + (i / 10) * 80f), allWeapons[i], Rarity.LEGENDARY))
    }
    for (i in AmmoType.entries.indices) {
        items.add(GroundItem.AmmoItem(id++, Vec2(center.x - 400f + i * 80f, center.y + 500f), AmmoType.entries[i], 500, Rarity.COMMON))
    }

    return GameState(
        players = players, groundItems = items, obstacles = obstacles, lootCrates = crates,
        battleZone = BattleZone(
            currentRadius = 10000f, targetRadius = 10000f, startRadius = 10000f, 
            centerX = center.x, centerY = center.y, damagePerSec = 0f
        ),
        mapWidth = mapW, mapHeight = mapH,
        cameraX = center.x, cameraY = center.y,
        nextId = id, zoomLevel = 1.8f
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
        s = updateMeleeSwings(s, dt)
        s = updateProjectiles(s, dt)
        s = updateHitscanBeams(s, dt)
        s = updateGrenades(s, dt)
        s = updateExplosions(s, dt)
        s = updateEffectZones(s, dt)
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

    private fun updateHitscanBeams(state: GameState, dt: Float): GameState {
        val newBeams = state.hitscanBeams.mapNotNull {
            val t = it.timer - dt
            if (t > 0f) it.copy(timer = t) else null
        }
        return state.copy(hitscanBeams = newBeams)
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

            // Laserschwert Passiver Energieverbrauch
            var newEnergyTimer = p.energyConsumeTimer
            if (p.inventory.activeWeapon == WeaponType.LASER_SWORD) {
                newEnergyTimer -= dt
                if (newEnergyTimer <= 0f) {
                    val reserve = p.inventory.reserveAmmo[AmmoType.ENERGY] ?: 0
                    if (reserve > 0) {
                        val newReserves = p.inventory.reserveAmmo.toMutableMap()
                        newReserves[AmmoType.ENERGY] = reserve - 1
                        p = p.copy(inventory = p.inventory.copy(reserveAmmo = newReserves))
                        newEnergyTimer = 1.0f // 1 Energie pro Sekunde
                    }
                }
            } else {
                newEnergyTimer = 0f
            }
            p = p.copy(energyConsumeTimer = newEnergyTimer)

            if (!p.isLocalPlayer) return@map p // Remote-Spieler & Bots laufen nicht per Maus
            if (newSe.stunTimer > 0f) return@map p  // Stun: kein Input

            // Mausrichtung (zoom-korrigiert)
            val zoom = state.zoomLevel
            val toMouse = (mousePos - Vec2(viewport.x / 2, viewport.y / 2)) * (1f / zoom)
            val dist = toMouse.length()

            // Bewegung erlaubt AUCH während Spawn (nur Schießen gesperrt)
            if (!isRightMouseDown && dist > 5f) {
                val dir = toMouse.normalized()
                val isAttackingWithChainsaw = state.meleeSwings.any { it.ownerId == p.id && it.weapon == WeaponType.CHAINSAW }
                val speedMod = when {
                    p.isSpawning      -> 0.5f          // langsamer beim Landen
                    newSe.slowTimer > 0f -> newSe.slowFactor
                    isAttackingWithChainsaw -> 1.35f   // 35% schneller beim Angreifen mit der Kettensäge
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
        var crates = state.lootCrates.toMutableList()
        var newItems = state.groundItems.toMutableList()
        var nextId = state.nextId

        for (proj in state.projectiles) {
            val oldPos = proj.pos
            val newPos = (oldPos + proj.velocity * dt)
            val p = proj.copy(pos = newPos, lifeTime = proj.lifeTime - dt)
            
            fun addExp(at: Vec2) { if (p.isExplosive) newExp.add(Explosion(at, p.explosionRadius, damage = p.damage)) }
            
            if (p.lifeTime <= 0f) { addExp(newPos); continue }
            
            // Kollision mit Hindernissen auf dem Pfad
            val hitObstacle = state.obstacles.firstOrNull { it.intersectsSegment(oldPos, newPos, p.radius) }
            if (hitObstacle != null) {
                if (p.bouncesRemaining > 0) {
                    val hitX = oldPos.x < hitObstacle.pos.x - 2f || oldPos.x > hitObstacle.pos.x + hitObstacle.width + 2f
                    val hitY = oldPos.y < hitObstacle.pos.y - 2f || oldPos.y > hitObstacle.pos.y + hitObstacle.height + 2f
                    val reflectedVel = Vec2(
                        if (hitX) -p.velocity.x else p.velocity.x,
                        if (hitY) -p.velocity.y else p.velocity.y
                    )
                    remaining.add(p.copy(pos = oldPos, velocity = reflectedVel, bouncesRemaining = p.bouncesRemaining - 1))
                } else {
                    addExp(newPos)
                }
                continue
            }
            
            if (newPos.x < 0 || newPos.x > state.mapWidth || newPos.y < 0 || newPos.y > state.mapHeight) { 
                addExp(newPos); continue 
            }
            
            var hit = false
            for (i in players.indices) {
                val t = players[i]; if (!t.isAlive || t.isSpawning || t.id == p.ownerId) continue
                if (intersectsSegmentCircle(oldPos, newPos, t.pos, PLAYER_RADIUS + p.radius)) {
                    val newHp = t.hp - p.damage
                    val newSe = if (p.stunDuration > 0f) t.statusEffects.copy(stunTimer = t.statusEffects.stunTimer + p.stunDuration) else t.statusEffects
                    players[i] = t.copy(hp = newHp.coerceAtLeast(0f), isAlive = newHp > 0f, lastDamagedBy = p.ownerId, statusEffects = newSe)
                    addExp(newPos); hit = true; break
                }
            }

            // Kollision mit Loot-Kisten
            if (!hit) {
                val crateIdx = crates.indexOfFirst { it.intersectsCircle(newPos, p.radius) }
                if (crateIdx >= 0) {
                    val crate = crates[crateIdx]
                    val newCrateHp = crate.hp - p.damage
                    if (newCrateHp <= 0f) {
                        // Kiste zerstört → Loot spawnen
                        val loot = MapGenerator.generateCrateLoot(crate, nextId)
                        newItems.addAll(loot)
                        nextId += loot.size
                        crates.removeAt(crateIdx)
                    } else {
                        crates[crateIdx] = crate.copy(hp = newCrateHp)
                    }
                    addExp(newPos); hit = true
                }
            }

            if (!hit) remaining.add(p)
        }
        return state.copy(projectiles = remaining, players = players, explosions = state.explosions + newExp,
            lootCrates = crates, groundItems = newItems, nextId = nextId)
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
        var crates = state.lootCrates.toMutableList()
        var newItems = state.groundItems.toMutableList()
        var projectiles = state.projectiles.toMutableList()
        var nextId = state.nextId
        val hitCrateIds = mutableSetOf<Int>()

        for (swing in state.meleeSwings) {
            var currentSwing = swing
            
            val owner = players.firstOrNull { it.id == currentSwing.ownerId }
            if (owner != null) {
                currentSwing = currentSwing.copy(pos = owner.pos)
            }
            
            val tipPos = currentSwing.pos + currentSwing.direction * currentSwing.range
            val newlyHit = mutableSetOf<Int>()
            
            // Projektile abwehren durch Katana
            if (currentSwing.weapon == WeaponType.KATANA) {
                for (j in projectiles.indices) {
                    val proj = projectiles[j]
                    if (proj.ownerId == currentSwing.ownerId) continue
                    if (proj.pos.distanceTo(currentSwing.pos) < currentSwing.range + proj.radius) {
                        val reflectionNormal = currentSwing.direction
                        val dot = proj.velocity.x * reflectionNormal.x + proj.velocity.y * reflectionNormal.y
                        if (dot < 0) {
                            val reflectedVel = Vec2(proj.velocity.x - 2 * dot * reflectionNormal.x, proj.velocity.y - 2 * dot * reflectionNormal.y)
                            projectiles[j] = proj.copy(velocity = reflectedVel, ownerId = currentSwing.ownerId)
                        }
                    }
                }
            }
            
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

            // Kisten-Schaden durch Melee
            for (i in crates.indices.reversed()) {
                val crate = crates[i]
                if (hitCrateIds.contains(crate.id)) continue
                if (crate.intersectsCircle(tipPos, PLAYER_RADIUS * 2f)) {
                    hitCrateIds.add(crate.id)
                    val newCrateHp = crate.hp - currentSwing.damage
                    if (newCrateHp <= 0f) {
                        val loot = MapGenerator.generateCrateLoot(crate, nextId)
                        newItems.addAll(loot)
                        nextId += loot.size
                        crates.removeAt(i)
                    } else {
                        crates[i] = crate.copy(hp = newCrateHp)
                    }
                }
            }
            
            val ns = currentSwing.copy(
                timer = currentSwing.timer - dt,
                hitPlayerIds = currentSwing.hitPlayerIds + newlyHit
            )
            if (ns.timer > 0f) remaining.add(ns)
        }
        return state.copy(meleeSwings = remaining, players = players,
            lootCrates = crates, groundItems = newItems, nextId = nextId)
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
                val nextTarget = if (bz.phase + 1 == 0) (bz.targetRadius * 0.6f) else (bz.targetRadius * 0.5f)
                bz = bz.copy(
                    isShrinking = false, 
                    phaseTimer = bz.waitDuration, 
                    currentRadius = bz.targetRadius,
                    targetRadius = nextTarget.coerceAtLeast(150f),
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

        val botsToShoot = mutableListOf<Int>()

        for (i in players.indices) {
            var bot = players[i]
            if (bot.isLocalPlayer || !bot.isAlive || !bot.isBot) continue
            if (bot.isDummyBot) {
                if (bot.isShootingDummy) {
                    bot = bot.copy(rotation = bot.wanderAngle) // fest in eine Richtung schauen
                    if (bot.fireCooldown <= 0f && bot.inventory.activeWeapon != null) {
                        botsToShoot.add(bot.id)
                    }
                }
                players[i] = bot
                continue 
            }
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

            // ── Ohne Schusswaffe: nächste Loot-Kiste ansteuern ───────────────
            val hasRanged = bot.inventory.gunSlots.any { it != null }
            if (!hasRanged) {
                val nearestCrate = state.lootCrates.minByOrNull { it.pos.distanceTo(bot.pos) }
                
                if (nearestCrate != null) {
                    val toCrate = nearestCrate.pos - bot.pos
                    if (toCrate.length() < PLAYER_RADIUS * 2.5f) {
                        // Kiste schlagen
                        val swingDir = toCrate.normalized()
                        bot = bot.copy(rotation = atan2(swingDir.y, swingDir.x))
                        if (bot.fireCooldown <= 0f) {
                            val activeWeapon = bot.inventory.activeWeapon
                            val ms = MeleeSwing(ownerId = bot.id, pos = bot.pos, direction = swingDir,
                                weapon = activeWeapon ?: WeaponType.FISTS,
                                range = activeWeapon?.range ?: 40f, damage = activeWeapon?.damage ?: 10f, knockback = 0f,
                                isLeft = Random.nextBoolean())
                            newMeleeSwings.add(ms)
                            bot = bot.copy(fireCooldown = activeWeapon?.fireRate ?: 0.3f)
                        }
                    } else {
                        // zur Kiste laufen
                        val dir = (toCrate + separation).normalized()
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
                        bot = bot.copy(isReloading = true, reloadTimer = weaponStats(activeWeapon, rarity).reloadTime)
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

        var finalState = state.copy(players = players, projectiles = state.projectiles + newProjectiles,
            meleeSwings = state.meleeSwings + newMeleeSwings, nextId = nextId, groundItems = updatedGroundItems)
            
        for (id in botsToShoot) {
            finalState = shoot(finalState, id)
        }
        return finalState
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
                        if (it.id == playerId) it.copy(isReloading = true, reloadTimer = weaponStats(weapon, rarity).reloadTime) else it
                    })
                }
                return state
            }
        }

        var newState = state; var nextId = state.nextId
        val stats = weaponStats(weapon, rarity)
        val damage = stats.damage
        val fireRate = stats.fireRate

        if (weapon.isMelee) {
            var newInv = inv
            if (weapon.ammoType != null) {
                val reserve = inv.reserveAmmo[weapon.ammoType] ?: 0
                if (reserve <= 0) return state // Kann nicht schlagen ohne Munition
                
                if (weapon == WeaponType.CHAINSAW) {
                    val newReserves = inv.reserveAmmo.toMutableMap()
                    newReserves[weapon.ammoType] = reserve - 1
                    newInv = inv.copy(reserveAmmo = newReserves)
                }
            }
            newState = newState.copy(players = newState.players.map {
                if (it.id == playerId) it.copy(inventory = newInv) else it
            })

            val dir = Vec2(cos(player.rotation), sin(player.rotation))
            val isLeft = !player.lastMeleeLeft
            val swingDuration = when (weapon) {
                WeaponType.KATANA -> 0.35f
                WeaponType.CHAINSAW -> 1f / fireRate
                else -> 0.15f
            }
            newState = newState.copy(
                meleeSwings = newState.meleeSwings + MeleeSwing(playerId, weapon, isLeft, player.pos, dir, weapon.range, damage, weapon.knockback, timer = swingDuration, maxTimer = swingDuration),
                players = newState.players.map { if (it.id == playerId) it.copy(lastMeleeLeft = isLeft) else it }
            )
        } else if (weapon == WeaponType.LASER_BEAM || weapon == WeaponType.ENERGY_RIFLE) {
            val clipIdx = inv.selectedSlotIndex - 1
            val newClips = inv.clipAmmo.toMutableList()
            if (clipIdx in newClips.indices) newClips[clipIdx] = (newClips[clipIdx] - 1).coerceAtLeast(0)
            
            newState = newState.copy(players = newState.players.map { 
                if (it.id == playerId) it.copy(inventory = it.inventory.copy(clipAmmo = newClips)) else it 
            })

            val startPos = player.pos + Vec2(cos(player.rotation), sin(player.rotation)) * (PLAYER_RADIUS + 2f)
            val path = mutableListOf<Vec2>(startPos)
            var currentPos = startPos
            var currentDir = Vec2(cos(player.rotation), sin(player.rotation))
            var bounces = if (weapon == WeaponType.LASER_BEAM) 2 else 0
            var remainingRange = weapon.range
            var hitscanPlayersHit = mutableSetOf<Int>()

            while (remainingRange > 0f) {
                val stepSize = 10f
                val maxSteps = (remainingRange / stepSize).toInt()
                var hitWall = false
                var endPos = currentPos + currentDir * remainingRange
                
                for (step in 1..maxSteps) {
                    val pTest = currentPos + currentDir * (step * stepSize)
                    
                    // Wand-Kollision
                    val obs = state.obstacles.firstOrNull { it.contains(pTest) }
                    if (obs != null) {
                        endPos = pTest
                        hitWall = true
                        val prevTest = pTest - currentDir * stepSize
                        val hitX = prevTest.x <= obs.pos.x || prevTest.x >= obs.pos.x + obs.width
                        val hitY = prevTest.y <= obs.pos.y || prevTest.y >= obs.pos.y + obs.height
                        if (hitX) currentDir = Vec2(-currentDir.x, currentDir.y)
                        if (hitY) currentDir = Vec2(currentDir.x, -currentDir.y)
                        if (!hitX && !hitY) currentDir = Vec2(-currentDir.x, -currentDir.y)
                        break
                    }
                    
                    // Spieler-Kollision
                    for (t in newState.players) {
                        if (t.id == playerId || !t.isAlive || hitscanPlayersHit.contains(t.id)) continue
                        if (t.pos.distanceTo(pTest) < PLAYER_RADIUS) {
                            hitscanPlayersHit.add(t.id)
                            val newHp = t.hp - damage
                            val stun = if (weapon == WeaponType.ENERGY_RIFLE) 3f else 0f
                            val newSe = if (stun > 0f) t.statusEffects.copy(stunTimer = t.statusEffects.stunTimer + stun) else t.statusEffects
                            newState = newState.copy(players = newState.players.map { 
                                if (it.id == t.id) it.copy(hp = newHp.coerceAtLeast(0f), isAlive = newHp > 0f, lastDamagedBy = playerId, statusEffects = newSe) 
                                else it 
                            })
                        }
                    }
                }
                
                path.add(endPos)
                remainingRange -= currentPos.distanceTo(endPos)
                
                if (hitWall && bounces > 0) {
                    bounces--
                    currentPos = endPos
                } else {
                    break
                }
            }

            newState = newState.copy(hitscanBeams = newState.hitscanBeams + HitscanBeam(path = path, color = weapon.color, thickness = if (weapon == WeaponType.ENERGY_RIFLE) 6f else 3f))
        } else {
            val clipIdx = inv.selectedSlotIndex - 1
            val newClips = inv.clipAmmo.toMutableList()
            if (clipIdx in newClips.indices) newClips[clipIdx] = (newClips[clipIdx] - 1).coerceAtLeast(0)
            
            val isLeft = !player.lastShotLeft
            newState = newState.copy(players = newState.players.map { 
                if (it.id == playerId) it.copy(inventory = it.inventory.copy(clipAmmo = newClips), lastShotLeft = isLeft) else it 
            })

            val spreadCount = if (weapon == WeaponType.FLAMETHROWER) 5 else if (weapon == WeaponType.SHOTGUN) 8 else 1
            val spread = if (weapon == WeaponType.FLAMETHROWER) 0.3f else if (weapon == WeaponType.SHOTGUN) 0.5f else 0.02f
            
            val isDual = weapon == WeaponType.DUAL_PISTOL || weapon == WeaponType.DUAL_ENERGY_PISTOL
            
            repeat(spreadCount) { s ->
                val angle = player.rotation + (s - spreadCount / 2f) * spread / spreadCount.toFloat().coerceAtLeast(1f)
                val bDir = Vec2(cos(angle), sin(angle))
                
                var spawnPos = player.pos + bDir * (PLAYER_RADIUS + weapon.bulletRadius + 2f)
                if (isDual) {
                    val perp = Vec2(-bDir.y, bDir.x)
                    val offsetDir = if (isLeft) -1f else 1f
                    spawnPos = spawnPos + perp * (12f * offsetDir)
                }

                newState = newState.copy(projectiles = newState.projectiles + Projectile(
                    id = nextId++, ownerId = playerId,
                    pos = spawnPos,
                    velocity = bDir * weapon.bulletSpeed, damage = damage, radius = weapon.bulletRadius, color = weapon.color,
                    lifeTime = weapon.range / weapon.bulletSpeed, maxLifeTime = weapon.range / weapon.bulletSpeed,
                    isExplosive = weapon == WeaponType.ROCKET_LAUNCHER, explosionRadius = if (weapon == WeaponType.ROCKET_LAUNCHER) 150f else 0f,
                    bouncesRemaining = 0,
                    stunDuration = 0f
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
                    player.inventory.addWeapon(item.weaponType, item.rarity)
                } else {
                    val swapIdx = if (player.inventory.selectedSlotIndex in 1..3) player.inventory.selectedSlotIndex - 1 else 0
                    if (player.inventory.gunSlots.all { it != null }) {
                        droppedItem = GroundItem.WeaponItem(idCounter++, dropPos, player.inventory.gunSlots[swapIdx]!!, player.inventory.gunRarities[swapIdx])
                        player.inventory.addWeapon(item.weaponType, item.rarity, swapIdx)
                    } else {
                        player.inventory.addWeapon(item.weaponType, item.rarity)
                    }
                }
            }
            is GroundItem.GrenadeItem -> {
                if (player.inventory.grenadeSlots.all { it != null }) {
                    val swapIdx = if (player.inventory.selectedSlotIndex in 4..5) player.inventory.selectedSlotIndex - 4 else 0
                    droppedItem = GroundItem.GrenadeItem(idCounter++, dropPos, player.inventory.grenadeSlots[swapIdx]!!, player.inventory.grenadeRarities[swapIdx])
                    player.inventory.addGrenade(item.grenadeType, item.rarity, swapIdx)
                } else {
                    player.inventory.addGrenade(item.grenadeType, item.rarity)
                }
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
