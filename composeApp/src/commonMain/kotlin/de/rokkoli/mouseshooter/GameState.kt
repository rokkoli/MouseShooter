package de.rokkoli.mouseshooter

import kotlin.math.*

enum class AmmoType(val label: String, val color: Long) {
    LIGHT("Leicht", 0xFFDDDD88),
    HEAVY("Schwer", 0xFF888844),
    SHELLS("Schrot", 0xFF884444),
    ROCKETS("Raketen", 0xFFCC8844),
    OIL("Öl", 0xFFFF6600),
    ENERGY("Energiemunition", 0xFF00AAFF)
}

// ─── Waffentypen ────────────────────────────────────────────────────────────
enum class WeaponType(
    val label: String,
    val damage: Float,
    val fireRate: Float,   // Schüsse/Sek
    val bulletSpeed: Float,
    val bulletRadius: Float,
    val range: Float,
    val color: Long,
    val isMelee: Boolean = false,
    val knockback: Float = 0f,
    val ammoType: AmmoType? = null,
    val clipSize: Int = 0,
    val reloadTime: Float = 0f
) {
    FISTS("Fäuste",          2f,  2f,   0f,  0f,  80f, 0xFFFFAAAA, true, 180f),
    KNIFE("Messer",          8f, 4f,   0f,  0f,  70f, 0xFFCCCCCC, true,  70f),
    LONG_KNIFE("Langmesser", 12f, 2.5f, 0f,  0f, 100f, 0xFFAABBCC, true,  90f),
    BOXING_GLOVES("Boxhandschuhe", 3f, 5f, 0f, 0f, 75f, 0xFFFF6600, true, 380f),
    PISTOL("Pistole",        10f, 2f,  800f, 2.5f, 800f, 0xFFFFDD00, false, 0f, AmmoType.LIGHT, 12, 1.5f),
    SMG("Maschinengewehr",    7f, 8f,  900f, 2f, 700f, 0xFF00AAFF, false, 0f, AmmoType.LIGHT, 30, 2.0f),
    SHOTGUN("Schrotflinte",  11f, 1f,  850f, 2f, 450f, 0xFF884444, false, 0f, AmmoType.SHELLS, 1, 2.5f),
    FLAMETHROWER("Flammenwerfer", 3f, 30f, 300f, 3f, 250f, 0xFFFF4400, false, 0f, AmmoType.OIL, 100, 3.0f),
    ROCKET_LAUNCHER("Raketenwerfer", 35f, 0.5f, 350f, 8f, 900f, 0xFFFF8800, false, 0f, AmmoType.ROCKETS, 1, 3.5f),
    MINIGUN("Minigun", 5f, 14f, 1100f, 2f, 750f, 0xFF4455FF, false, 0f, AmmoType.LIGHT, 100, 5.0f),
    SNIPER("Sniper", 50f, 0.1f, 4000f, 2f, 10000f, 0xFFFF0033, false, 0f, AmmoType.HEAVY, 5, 4.0f),
    DUAL_ENERGY_PISTOL("Energiepistolen", 1.15f, 28f, 1000f, 2f, 800f, 0xFFAAAAFF, false, 0f, AmmoType.ENERGY, 60, 2.0f),
    ENERGY_RIFLE("Energiegewehr", 12f, 1f, 0f, 3f, 300f, 0xFF44AAFF, false, 50f, AmmoType.ENERGY, 5, 3.0f),
    DUAL_PISTOL("Doppelpistolen", 10f, 4f, 800f, 2.5f, 800f, 0xFFFFDD00, false, 0f, AmmoType.LIGHT, 24, 2.0f),
    LASER_BEAM("Laserstrahl", 15f, 1f, 0f, 3f, 2000f, 0xFF00FFFF, false, 0f, AmmoType.ENERGY, 5, 3.0f),
    CHAINSAW("Kettensäge", 10f, 2f, 0f, 0f, 70f, 0xFFBBBBBB, true, 50f, AmmoType.OIL),
    LASER_SWORD("Laserschwert", 15f, 2.5f, 0f, 0f, 120f, 0xFF00FF00, true, 80f, AmmoType.ENERGY),
    KATANA("Katana", 7f, 2.5f, 0f, 0f, 80f, 0xFFDDDDDD, true, 60f);
}

enum class GrenadeType(val label: String, val color: Long) {
    NORMAL("Granate", 0xFF44FF44),
    CLUSTER("Streugranate", 0xFFFFAA00),
    ELECTRIC("Elektrisiergranate", 0xFF8800FF),
    BAND("Bandgranate", 0xFF00FFFF),
    SMOKE("Rauchgranate", 0xFF888888),
    FLASH("Blendgranate", 0xFFFFFFFF),
    MEDKIT("Medkit", 0xFFFF0000);
}

enum class ArmorType(val label: String, val color: Long) {
    MILITARY("Militärrüstung", 0xFF556655),
    STEALTH("Tarnrüstung", 0xFF334433),
    AGILITY("Agilitätsrüstung", 0xFF336655);
}

// ─── Rarity ─────────────────────────────────────────────────────────────────
enum class Rarity(val glowColor: Long, val label: String, val damageMod: Float, val fireRateMod: Float, val reloadMod: Float) {
    COMMON(0xFF888888, "Häufig", 1.0f, 1.0f, 1.0f),
    UNCOMMON(0xFF4488FF, "Ungewöhnlich", 1.15f, 1.05f, 0.95f),
    RARE(0xFF44FF44, "Selten", 1.30f, 1.15f, 0.85f),
    EPIC(0xFFAA44FF, "Episch", 1.50f, 1.30f, 0.70f),
    MYTHIC(0xFFFF2222, "Mythisch", 1.75f, 1.45f, 0.60f),
    LEGENDARY(0xFFFFFF00, "Legendär", 2.0f, 1.60f, 0.50f);
}

fun rarityFromDistance(dist: Float, maxDist: Float, random: kotlin.random.Random = kotlin.random.Random): Rarity {
    // Wunsch: Seltene Waffen können überall spawnen, auch innen
    val r = random.nextFloat()
    return when {
        r < 0.45f -> Rarity.COMMON
        r < 0.70f -> Rarity.UNCOMMON
        r < 0.86f -> Rarity.RARE
        r < 0.94f -> Rarity.EPIC
        r < 0.98f -> Rarity.MYTHIC
        else      -> Rarity.LEGENDARY
    }
}

// ─── Vec2 ────────────────────────────────────────────────────────────────────
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(f: Float) = Vec2(x * f, y * f)
    fun length() = sqrt(x * x + y * y)
    fun normalized(): Vec2 {
        val l = length(); return if (l < 0.0001f) Vec2(0f, 0f) else Vec2(x / l, y / l)
    }
    fun dot(o: Vec2) = x * o.x + y * o.y
    fun distanceTo(o: Vec2) = (this - o).length()
}

// ─── Hindernisse ─────────────────────────────────────────────────────────────
data class Obstacle(
    val pos: Vec2,
    val width: Float,
    val height: Float,
    val color: Long = 0xFF555566
) {
    fun contains(p: Vec2): Boolean =
        p.x >= pos.x && p.x <= pos.x + width && p.y >= pos.y && p.y <= pos.y + height

    fun intersectsCircle(center: Vec2, radius: Float): Boolean {
        val cx = center.x.coerceIn(pos.x, pos.x + width)
        val cy = center.y.coerceIn(pos.y, pos.y + height)
        return center.distanceTo(Vec2(cx, cy)) < radius
    }

    fun intersectsSegment(p1: Vec2, p2: Vec2, radius: Float): Boolean {
        val dist = p1.distanceTo(p2)
        val steps = (dist / 15f).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val p = p1 + (p2 - p1) * t
            if (intersectsCircle(p, radius)) return true
        }
        return false
    }
}

// ─── Ground Items ─────────────────────────────────────────────────────────────
sealed class GroundItem(open val id: Int, open val pos: Vec2, open val rarity: Rarity) {
    data class WeaponItem(
        override val id: Int,
        override val pos: Vec2,
        val weaponType: WeaponType,
        override val rarity: Rarity,
        var glowPhase: Float = 0f
    ) : GroundItem(id, pos, rarity)

    data class GrenadeItem(
        override val id: Int,
        override val pos: Vec2,
        val grenadeType: GrenadeType,
        override val rarity: Rarity,
        var glowPhase: Float = 0f
    ) : GroundItem(id, pos, rarity)

    data class ArmorItem(
        override val id: Int,
        override val pos: Vec2,
        val armorType: ArmorType,
        override val rarity: Rarity,
        var glowPhase: Float = 0f
    ) : GroundItem(id, pos, rarity)

    data class AmmoItem(
        override val id: Int,
        override val pos: Vec2,
        val ammoType: AmmoType,
        val amount: Int,
        override val rarity: Rarity,
        var glowPhase: Float = 0f
    ) : GroundItem(id, pos, rarity)
}

// ─── Statuseffekte ───────────────────────────────────────────────────────────
data class StatusEffects(
    var stunTimer: Float = 0f,
    var slowTimer: Float = 0f,
    var slowFactor: Float = 1f,
    var blindTimer: Float = 0f,
    var invisibleTimer: Float = 0f,
    var dashCooldown: Float = 0f,
    var dashTimer: Float = 0f,
    var dashVelocity: Vec2 = Vec2(0f, 0f),
    var healTimer: Float = 0f,
    var healRemaining: Float = 0f
)

// ─── Inventar-Slot ────────────────────────────────────────────────────────────
data class Inventory(
    val meleeSlot: WeaponType? = WeaponType.FISTS,
    val meleeRarity: Rarity = Rarity.COMMON,
    val gunSlots: List<WeaponType?> = listOf(null, null, null),
    val gunRarities: List<Rarity> = listOf(Rarity.COMMON, Rarity.COMMON, Rarity.COMMON),
    val grenadeSlots: List<GrenadeType?> = listOf(null, null),
    val grenadeRarities: List<Rarity> = listOf(Rarity.COMMON, Rarity.COMMON),
    val armorSlot: ArmorType? = null,
    val armorRarity: Rarity? = null,
    val selectedSlotIndex: Int = 0,  // 0=melee, 1-3=guns, 4-5=grenades, 6=armor
    val clipAmmo: List<Int> = listOf(0, 0, 0), // Ammo in Magazin für gunSlots
    val reserveAmmo: Map<AmmoType, Int> = mapOf(
        AmmoType.LIGHT to 0,
        AmmoType.HEAVY to 0,
        AmmoType.SHELLS to 0,
        AmmoType.ROCKETS to 0,
        AmmoType.OIL to 0,
        AmmoType.ENERGY to 0
    )
) {
    val activeWeapon: WeaponType? get() = when {
        selectedSlotIndex == 0 -> meleeSlot
        selectedSlotIndex in 1..3 -> gunSlots.getOrNull(selectedSlotIndex - 1)
        else -> null
    }
    val activeGrenade: GrenadeType? get() = when {
        selectedSlotIndex in 4..5 -> grenadeSlots.getOrNull(selectedSlotIndex - 4)
        else -> null
    }

    fun scrollNext(): Inventory {
        var next = (selectedSlotIndex + 1) % 7
        repeat(7) {
            val hasItem = when {
                next == 0 -> meleeSlot != null
                next in 1..3 -> gunSlots.getOrNull(next - 1) != null
                next in 4..5 -> grenadeSlots.getOrNull(next - 4) != null
                next == 6 -> armorSlot != null
                else -> false
            }
            if (hasItem) return this.copy(selectedSlotIndex = next)
            next = (next + 1) % 7
        }
        return this
    }

    fun scrollPrev(): Inventory {
        var prev = (selectedSlotIndex - 1 + 7) % 7
        repeat(7) {
            val hasItem = when {
                prev == 0 -> meleeSlot != null
                prev in 1..3 -> gunSlots.getOrNull(prev - 1) != null
                prev in 4..5 -> grenadeSlots.getOrNull(prev - 4) != null
                prev == 6 -> armorSlot != null
                else -> false
            }
            if (hasItem) return this.copy(selectedSlotIndex = prev)
            prev = (prev - 1 + 7) % 7
        }
        return this
    }

    fun addWeapon(w: WeaponType, r: Rarity, swapIdx: Int? = null): Inventory {
        if (w.isMelee) return copy(meleeSlot = w, meleeRarity = r)
        val newGuns = gunSlots.toMutableList()
        val newRarities = gunRarities.toMutableList()
        val newClip = clipAmmo.toMutableList()
        
        val targetIdx = swapIdx ?: newGuns.indexOfFirst { it == null }.let { if (it >= 0) it else 0 }
        
        newGuns[targetIdx] = w
        newRarities[targetIdx] = r
        val clipSize = weaponStats(w, r).clipSize
        newClip[targetIdx] = clipSize
        
        return copy(gunSlots = newGuns, gunRarities = newRarities, clipAmmo = newClip)
    }

    fun addGrenade(g: GrenadeType, r: Rarity, swapIdx: Int? = null): Inventory {
        val newGrenades = grenadeSlots.toMutableList()
        val newRarities = grenadeRarities.toMutableList()
        
        val targetIdx = swapIdx ?: newGrenades.indexOfFirst { it == null }.let { if (it >= 0) it else 0 }
        
        newGrenades[targetIdx] = g
        newRarities[targetIdx] = r
        return copy(grenadeSlots = newGrenades, grenadeRarities = newRarities)
    }

    fun addArmor(a: ArmorType, r: Rarity): Inventory = copy(armorSlot = a, armorRarity = r)
}

// ─── Spieler ──────────────────────────────────────────────────────────────────
data class Player(
    val id: Int,
    val pos: Vec2,
    val velocity: Vec2 = Vec2(0f, 0f),
    val rotation: Float = 0f,
    val hp: Float = 100f,
    val maxHp: Float = 100f,
    val inventory: Inventory = Inventory(),
    val fireCooldown: Float = 0f,
    val isLocalPlayer: Boolean = false,
    val isBot: Boolean = false,
    val isDummyBot: Boolean = false,
    val isShootingDummy: Boolean = false,
    val statusEffects: StatusEffects = StatusEffects(),
    val isAlive: Boolean = true,
    val color: Long = 0xFF00AAFF,
    val kills: Int = 0,
    // Fallschirm-Spawn
    val isSpawning: Boolean = true,
    val spawnTimer: Float = 3.5f,
    // Bot-Wandern
    val wanderAngle: Float = 0f,
    val wanderTimer: Float = 0f,
    val spreadAngle: Float = 0f,   // eindeutige Streurichtung beim Spawn
    val hasDroppedLoot: Boolean = false,
    val lastDamagedBy: Int = -1,
    val lastMeleeLeft: Boolean = false,
    val lastShotLeft: Boolean = false,
    val isMovingIntent: Boolean = false, // Neu für Multiplayer-Bewegung
    val isReloading: Boolean = false,
    val reloadTimer: Float = 0f,
    val energyConsumeTimer: Float = 0f
)

// ─── Projektile ──────────────────────────────────────────────────────────────
data class Projectile(
    val id: Int,
    val ownerId: Int,
    val pos: Vec2,
    val velocity: Vec2,
    val damage: Float,
    val radius: Float,
    val color: Long,
    val lifeTime: Float,
    val maxLifeTime: Float,
    val isExplosive: Boolean = false,
    val explosionRadius: Float = 0f,
    val bouncesRemaining: Int = 0,
    val stunDuration: Float = 0f
)

// ─── Explosionen ─────────────────────────────────────────────────────────────
data class Explosion(
    val pos: Vec2,
    val maxRadius: Float,
    var currentRadius: Float = 0f,
    var timer: Float = 0f,
    val duration: Float = 0.4f,
    val damage: Float = 0f,
    val color: Long = 0xFFFF8800,
    var hasDealtDamage: Boolean = false,
    val ownerId: Int = -1
)

// ─── Granaten ────────────────────────────────────────────────────────────────
data class ThrownGrenade(
    val id: Int,
    val ownerId: Int,
    val grenadeType: GrenadeType,
    val pos: Vec2,
    val velocity: Vec2,
    var timer: Float,
    val fuseTime: Float = 2.5f
)

// ─── Statuseffekt-Zonen ──────────────────────────────────────────────────────
data class EffectZone(
    val id: Int,
    val pos: Vec2,
    val radius: Float,
    val type: ZoneType,
    var timer: Float,
    val duration: Float,
    val color: Long,
    val alpha: Float = 0.4f
)

enum class ZoneType { SMOKE, SLOW_FIELD, HEAL_FIELD }

// ─── Kampfzone (schrumpfend) ─────────────────────────────────────────────────
data class BattleZone(
    val currentRadius: Float,
    val targetRadius: Float,
    val startRadius: Float,
    val centerX: Float,
    val centerY: Float,
    val damagePerSec: Float = 8f,
    val isShrinking: Boolean = false,
    val phaseTimer: Float = 50f,   // Start-Verzögerung
    val waitDuration: Float = 15f,
    val shrinkDuration: Float = 12f,
    val phase: Int = 0
) {
    val effectiveRadius: Float get() = currentRadius
}

// ─── Melee-Hitbox ────────────────────────────────────────────────────────────
data class MeleeSwing(
    val ownerId: Int,
    val weapon: WeaponType,
    val isLeft: Boolean,
    val pos: Vec2,
    val direction: Vec2,
    val range: Float,
    val damage: Float,
    val knockback: Float,
    var timer: Float = 0.15f,
    val maxTimer: Float = 0.15f,
    val hitPlayerIds: Set<Int> = emptySet()
)

// ─── Loot-Kisten ─────────────────────────────────────────────────────────────
data class LootCrate(
    val id: Int,
    val pos: Vec2,
    val rarity: Rarity,
    val hp: Float = 30f,
    val maxHp: Float = 30f,
    val width: Float = 40f,
    val height: Float = 22f,
) {
    fun contains(p: Vec2): Boolean =
        p.x >= pos.x - width / 2 && p.x <= pos.x + width / 2 &&
        p.y >= pos.y - height / 2 && p.y <= pos.y + height / 2

    fun intersectsCircle(center: Vec2, radius: Float): Boolean {
        val cx = center.x.coerceIn(pos.x - width / 2, pos.x + width / 2)
        val cy = center.y.coerceIn(pos.y - height / 2, pos.y + height / 2)
        return center.distanceTo(Vec2(cx, cy)) < radius
    }
}

// ─── Hitscan Beams ────────────────────────────────────────────────────────────
data class HitscanBeam(
    val path: List<Vec2>,
    var timer: Float = 0.15f,
    val maxTimer: Float = 0.15f,
    val color: Long,
    val thickness: Float = 3f
)

// ─── Hauptspielzustand ────────────────────────────────────────────────────────
data class GameState(
    val players: List<Player> = emptyList(),
    val projectiles: List<Projectile> = emptyList(),
    val hitscanBeams: List<HitscanBeam> = emptyList(),
    val explosions: List<Explosion> = emptyList(),
    val grenades: List<ThrownGrenade> = emptyList(),
    val effectZones: List<EffectZone> = emptyList(),
    val groundItems: List<GroundItem> = emptyList(),
    val obstacles: List<Obstacle> = emptyList(),
    val lootCrates: List<LootCrate> = emptyList(),
    val battleZone: BattleZone = BattleZone(2000f, 200f, 200f, 0f, 0f),
    val meleeSwings: List<MeleeSwing> = emptyList(),
    val mapWidth: Float = 5500f,
    val mapHeight: Float = 5500f,
    val gameTime: Float = 0f,
    val isGameOver: Boolean = false,
    val winnerId: Int = -1,
    val cameraX: Float = 0f,
    val cameraY: Float = 0f,
    val nextId: Int = 1000,
    val zoomLevel: Float = 1.8f,
    val killFeed: List<String> = emptyList(),
    val spectatedPlayerId: Int? = null
)

data class WeaponStats(
    val damage: Float,
    val fireRate: Float,
    val reloadTime: Float,
    val clipSize: Int
)

fun weaponStats(weapon: WeaponType, rarity: Rarity): WeaponStats {
    val damageMod = when (rarity) {
        Rarity.COMMON -> 1.0f
        Rarity.UNCOMMON -> 1.15f
        Rarity.RARE -> 1.30f
        Rarity.EPIC -> 1.50f
        Rarity.MYTHIC -> 1.75f
        Rarity.LEGENDARY -> 2.00f
    }
    
    val fireRateMod = when (rarity) {
        Rarity.COMMON -> 1.0f
        Rarity.UNCOMMON -> 1.05f
        Rarity.RARE -> 1.15f
        Rarity.EPIC -> 1.30f
        Rarity.MYTHIC -> 1.45f
        Rarity.LEGENDARY -> 1.60f
    }
    
    val reloadMod = when (rarity) {
        Rarity.COMMON -> 1.0f
        Rarity.UNCOMMON -> 0.95f
        Rarity.RARE -> 0.85f
        Rarity.EPIC -> 0.70f
        Rarity.MYTHIC -> 0.60f
        Rarity.LEGENDARY -> 0.50f
    }

    val baseDamage = weapon.damage
    val baseFireRate = weapon.fireRate
    val baseReloadTime = weapon.reloadTime
    var clipSize = weapon.clipSize

    if (weapon == WeaponType.SHOTGUN) {
        if (rarity.ordinal >= Rarity.EPIC.ordinal) {
            clipSize = 2
        }
    }
    
    val finalDamage = if (weapon == WeaponType.DUAL_ENERGY_PISTOL) {
        when (rarity) {
            Rarity.COMMON -> 1.2f
            Rarity.UNCOMMON -> 1.3f
            Rarity.RARE -> 1.5f
            Rarity.EPIC -> 1.7f
            Rarity.MYTHIC -> 2.0f
            Rarity.LEGENDARY -> 2.3f
        }
    } else {
        baseDamage * damageMod
    }

    return WeaponStats(
        damage = finalDamage,
        fireRate = baseFireRate * fireRateMod,
        reloadTime = if (weapon.isMelee) 0f else baseReloadTime * reloadMod,
        clipSize = clipSize
    )
}
