package de.rokkoli.mouseshooter

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform