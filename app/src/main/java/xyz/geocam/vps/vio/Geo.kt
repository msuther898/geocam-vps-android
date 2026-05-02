package xyz.geocam.vps.vio

import com.google.android.gms.maps.model.LatLng
import kotlin.math.PI
import kotlin.math.cos

object Geo {
    private const val M_PER_DEG_LAT = 111_320.0

    fun offsetLatLng(anchor: LatLng, eastMeters: Double, northMeters: Double): LatLng {
        val dLat = northMeters / M_PER_DEG_LAT
        val dLon = eastMeters / (M_PER_DEG_LAT * cos(anchor.latitude * PI / 180.0))
        return LatLng(anchor.latitude + dLat, anchor.longitude + dLon)
    }
}
