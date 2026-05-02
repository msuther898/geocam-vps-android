package xyz.geocam.vps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object Permissions {
    val RUNTIME = arrayOf(Manifest.permission.CAMERA)

    fun allGranted(context: Context, perms: Array<String> = RUNTIME): Boolean =
        perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
}
