package de.rokkoli.mouseshooter

// ---------------------------------------------------------------------------
// JVM stub — multiplayer is not supported on desktop
// ---------------------------------------------------------------------------

actual fun createMultiplayerConnector(): MultiplayerConnector =
    error("Multiplayer wird auf dieser Plattform nicht unterstützt")
