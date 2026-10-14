package app.rasoi.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import app.rasoi.Config
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One-shot location capture at send time. The result is only held in memory. */
class LocationSource(private val context: Context) : Locator {
    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    override fun permissionGranted() = granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)

    @SuppressLint("MissingPermission")
    override suspend fun capture(): LocationDto? {
        if (!permissionGranted()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            add(LocationManager.NETWORK_PROVIDER)
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) add(LocationManager.GPS_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        for (p in providers) {
            // ponytail: below API 30 there is no one-shot API, so use the last known fix; add a listener if needed.
            val loc: Location? = if (Build.VERSION.SDK_INT >= 30) {
                withTimeoutOrNull(Config.LOCATION_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        lm.getCurrentLocation(p, signal, context.mainExecutor) { cont.resume(it) }
                    }
                }
            } else lm.getLastKnownLocation(p)
            if (loc != null) return LocationDto(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy.toDouble() else null)
        }
        return null
    }
}
