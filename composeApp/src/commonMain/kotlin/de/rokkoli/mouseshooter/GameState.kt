package de.rokkoli.mouseshooter

import kotlin.math.*

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
    val knockback: Float = 0f
) {
    FISTS("Fäuste",          6f,  2f,   0f,  0f,  80f, 0xFFFFAAAA, true, 180f),
    KNIFE("Messer",          10f, 4f,   0f,  0f,  70f, 0xFFCCCCCC, true,  70f),
    LONG_KNIFE("Langmesser", 20f, 2.5f, 0f,  0f, 100f, 0xFFAABBCC, true,  90f),
    BOXING_GLOVES("Boxhandschuhe", 2f, 5f, 0f, 0f, 75f, 0xFFFF6600, true, 380f),
    PISTOL("Pistole",        14f, 2f,  800f, 2.5f, 800f, 0xFFFFDD00),
    SMG("Maschinengewehr",    7f, 8f,  900f, 2f, 700f, 0xFF00AAFF),
    SHOTGUN("Schrotflinte",  10f, 1f,  850f, 2f, 450f, 0xFF884444),
    FLAMETHROWER("Flammenwerfer", 3f, 30f, 300f, 3f, 250f, 0xFFFF4400),
    ROCKET_LAUNCHER("Raketenwerfer", 45f, 0.5f, 350f, 8f, 900f, 0xFFFF8800),
    MINIGUN("Minigun", 5f, 18f, 1100f, 2f, 750f, 0xFF4455FF),
    SNIPER("Sniper", 150f, 0.1f, 4000f, 2f, 10000f, 0xFFFF0033);
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
enum class Rarity(val glowColor: Long, val label: String) {
    COMMON(0xFF888888, "Häufig"),
    UNCOMMON(0xFF4488FF, "Ungewöhnlich"),
    RARE(0xFF44FF44, "Selten"),
    EPIC(0xFFAA44FF, "Episch"),
    LEGENDARY(0xFFFFFF00, "Legendär");
}

fun rarityFromDistance(dist: Float, maxDist: Float): Rarity {
    // Wunsch: Seltene Waffen können überall spawnen, auch innen
    val r = kotlin.random.Random.nextFloat()
    return when {
        r < 0.45f -> Rarity.COMMON
        r < 0.70f -> Rarity.UNCOMMON
        r < 0.88f -> Rarity.RARE
        r < 0.96f -> Rarity.EPIC
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
    val gunSlots: List<WeaponType?> = listOf(WeaponType.PISTOL, null, null),
    val grenadeSlots: List<GrenadeType?> = listOf(null, null),
    val armorSlot: ArmorType? = null,
    val selectedSlotIndex: Int = 1  // 0=melee, 1-3=guns, 4-5=grenades
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
        var next = (selectedSlotIndex + 1) % 6
        repeat(6) {
            val hasItem = when {
                next == 0 -> meleeSlot != null
                next in 1..3 -> gunSlots.getOrNull(next - 1) != null
                else -> grenadeSlots.getOrNull(next - 4) != null
            }
            if (hasItem) return this.copy(selectedSlotIndex = next)
            next = (next + 1) % 6
        }
        return this
    }

    fun scrollPrev(): Inventory {
        var prev = (selectedSlotIndex - 1 + 6) % 6
        repeat(6) {
            val hasItem = when {
                prev == 0 -> meleeSlot != null
                prev in 1..3 -> gunSlots.getOrNull(prev - 1) != null
                else -> grenadeSlots.getOrNull(prev - 4) != null
            }
            if (hasItem) return this.copy(selectedSlotIndex = prev)
            prev = (prev - 1 + 6) % 6
        }
        return this
    }

    fun addWeapon(w: WeaponType): Inventory {
        if (w.isMelee) return copy(meleeSlot = w)
        val newGuns = gunSlots.toMutableList()
        val freeIdx = newGuns.indexOfFirst { it == null }
        if (freeIdx >= 0) newGuns[freeIdx] = w else newGuns[0] = w
        return copy(gunSlots = newGuns)
    }

    fun addGrenade(g: GrenadeType): Inventory {
        val newGrenades = grenadeSlots.toMutableList()
        val freeIdx = newGrenades.indexOfFirst { it == null }
        if (freeIdx >= 0) newGrenades[freeIdx] = g else newGrenades[0] = g
        return copy(grenadeSlots = newGrenades)
    }

    fun addArmor(a: ArmorType): Inventory = copy(armorSlot = a)
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
    val lastMeleeLeft: Boolean = false
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
    val explosionRadius: Float = 0f
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
    val centerX: Float,
    val centerY: Float,
    val shrinkRate: Float = 8f,    // px pro Sekunde
    val damagePerSec: Float = 5f,
    var nextPhaseTimer: Float = 30f
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
    var timer: Float = 0.15f
)

// ─── Hauptspielzustand ────────────────────────────────────────────────────────
data class GameState(
    val players: List<Player> = emptyList(),
    val projectiles: List<Projectile> = emptyList(),
    val explosions: List<Explosion> = emptyList(),
    val grenades: List<ThrownGrenade> = emptyList(),
    val effectZones: List<EffectZone> = emptyList(),
    val groundItems: List<GroundItem> = emptyList(),
    val obstacles: List<Obstacle> = emptyList(),
    val battleZone: BattleZone = BattleZone(2000f, 200f, 0f, 0f),
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
    val killFeed: List<String> = emptyList()
)
