# Umfassender Implementierungsplan: KMP Top-Down Multiplayer Shooter

## 1. Projekt-Übersicht & Tech-Stack
Ein kompetitiver Top-Down-Shooter für **Desktop (JVM)** und **Web (Wasm)**, entwickelt mit **Kotlin Multiplatform**.

* **Engine/Rendering:** **KorGE** (Game-Loop, Physik, Sprite-Rendering) kombiniert mit **Compose Multiplatform** für das HUD/Inventar-UI.
* **Networking:** **Ktor WebSockets** für die Echtzeit-Synchronisation.
* **Architektur:** Server-Authoritative (Server berechnet Schaden und Positionen).

---

## 2. Steuerung (Mouse-Only)
Die Steuerung ist strikt auf die Maus begrenzt. Der Charakter ist permanent in der Bildschirmmitte fixiert.

* **Bewegung:** Charakter läuft konstant in Richtung des Mauszeigers.
* **Rechte Maustaste (Halten):** Charakter bleibt stehen (`velocity = 0`).
* **Linke Maustaste:** Schießen / Angreifen.
* **Mausrad (Scrollen):** Waffen wechseln (Inventar-Slots durchlaufen).
* **Mausrad (Drücken):** Item vom Boden aufheben.

---

## 3. Map-Design & Loot-Logik
* **Spawn:** Alle Spieler starten gleichzeitig in der Mitte der Map.
* **World-Loot:** Waffen spawnen am Boden.
    * **Rarity-System:** Je weiter ein Item vom Zentrum entfernt ist, desto seltener ist es (Farbliches Leuchten/Glow-Effekt) und desto höher ist der Schaden.
* **Die Zone:** Ein schrumpfender Ring, der von außen nach innen zieht und Schaden verursacht. Dies zwingt Spieler, für besseren Loot ins Risiko zu gehen (außerhalb des Zentrums).
* **Level-Details:** Statische Hindernisse wie Häuser und Mauern für Deckung.

---

## 4. Item- & Kampfsystem

### A. Waffen-Slots (Inventar unten rechts)
1.  **Nahkampf-Slot:** Messer, Langmesser oder **Boxhandschuhe** (spezieller Knockback-Effekt für Gegner).
2.  **Pistolen-Slots (3 Stück):** Pistole, Maschinengewehr, Flammenwerfer (Kegel-Schaden), Raketenwerfer (AoE-Explosion).
3.  **Extra-Item-Slots (2 Stück):** Für Granaten.
4.  **Rüstungs-Slot:** Ein Platz für Spezial-Ausrüstung.

### B. Granaten-Typen (Spezial-Effekte)
* **Normal:** Standard Explosionsschaden.
* **Streugranate:** Zerfällt nach Explosion in mehrere kleine Sprengkörper.
* **Elektrisiergranate:** Verursacht einen "Stun"-Status (Gegner kann sich kurz nicht bewegen/handeln).
* **Bandgranate:** Erzeugt ein Hindernis oder verlangsamt (Slow) Spieler im Radius massiv.
* **Rauchgranate:** Erzeugt eine dichte Rauchwolke für alle Spieler, die die Sicht auf Einheiten darunter blockiert.
* **Blendgranate:** Wer im Explosionsradius steht, bekommt einen **komplett weißen Screen** (Overlay). Man kann weiter schießen/laufen, sieht aber für die Dauer des Effekts nichts.

### C. Rüstungs-Klassen & Skills
* **Militärrüstung:** Erhöhte Schadensresistenz (Passiv).
* **Tarnrüstung:** Aktivierbarer Skill (5 Sek. Unsichtbarkeit für Gegner).
* **Agilitätsrüstung:** Passiver Speed-Boost + aktiver Skill "Dash" (schneller Sprung in Blickrichtung).

---

## 5. Phasen der Implementierung (für den Agenten)

### Phase 1: Core Framework & Input
Implementierung der Mouse-Only Steuerung und der zentrierten Kamera in KorGE. Aufbau des Spieler-Zustands (Position, Rotation, Geschwindigkeit).

### Phase 2: Physik & Nahkampf (Knockback)
Entwicklung des Vektor-basierten Physik-Systems. Integration der Boxhandschuhe: Wenn ein Schlag trifft, wird der Gegner in Schlagrichtung weggestoßen (Vorbereitung für den späteren Box-Modus).

### Phase 3: Loot- & Zonen-System
Generierung der Map mit Loot-Tabellen basierend auf der Distanz zum Zentrum. Implementierung des "Glow"-Shaders für seltene Items und der schrumpfenden Zone.

### Phase 4: Spezial-Effekte (Granaten & Skills)
Programmierung der Status-Effekte:
* `Screen-Overlay` für die Blendgranate.
* `Alpha-Transparency` für die Tarnung.
* `Stun-Lock` Logik für Elektro-Effekte.

### Phase 5: Networking & UI
Aufsetzen des Ktor-Servers zur Synchronisation der Spielerbewegungen und Projektile. Erstellen des HUDs mit Compose Multiplatform (Inventar-Slots, Cooldown-Anzeige für Skills).




