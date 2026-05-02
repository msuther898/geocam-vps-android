package xyz.geocam.vps.tiles

/**
 * Tile-source abstraction. Phase 1 only references this so the contract exists
 * before phase 3's cross-view matcher needs it.
 */
interface TileProvider {
    val name: String
    val maxZoom: Int
    suspend fun fetch(z: Int, x: Int, y: Int): ByteArray?
}
