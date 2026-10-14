package app.rasoi.sos

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import app.rasoi.BuildConfig

/**
 * sms flavor only. Reports what the OS tells us and nothing more:
 * HANDED_TO_NETWORK = sentIntent OK for every part, DELIVERY_REPORTED = network delivery receipt.
 * Neither means a person received or read it.
 */
object SmsBackend {
    const val enabled = true
    val permissions = arrayOf(Manifest.permission.SEND_SMS)
    val relayNumber: String get() = BuildConfig.SMS_RELAY_NUMBER

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun send(context: Context, to: String, text: String, onEvent: (SmsEvent) -> Unit) {
        val app = context.applicationContext
        try {
            val mgr = if (Build.VERSION.SDK_INT >= 31) app.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = mgr.divideMessage(text)
            val tag = System.nanoTime()
            val sentAction = "x.s.$tag"
            val deliveredAction = "x.d.$tag"

            fun receiver(action: String, onAll: (ok: Boolean) -> Unit) {
                var seen = 0
                var ok = true
                val r = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, i: Intent) {
                        if (resultCode != Activity.RESULT_OK) ok = false
                        if (++seen == parts.size) {
                            runCatching { app.unregisterReceiver(this) }
                            onAll(ok)
                        }
                    }
                }
                ContextCompat.registerReceiver(app, r, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            }
            // ponytail: a delivery receiver that never fires stays registered until process exit.
            receiver(sentAction) { ok -> onEvent(if (ok) SmsEvent.HANDED_TO_NETWORK else SmsEvent.FAILED) }
            receiver(deliveredAction) { ok -> if (ok) onEvent(SmsEvent.DELIVERY_REPORTED) }

            fun pending(action: String, i: Int) = PendingIntent.getBroadcast(
                app, i, Intent(action).setPackage(app.packageName), PendingIntent.FLAG_IMMUTABLE,
            )
            mgr.sendMultipartTextMessage(
                to, null, parts,
                ArrayList(parts.indices.map { pending(sentAction, it) }),
                ArrayList(parts.indices.map { pending(deliveredAction, it) }),
            )
        } catch (e: Exception) {
            onEvent(SmsEvent.FAILED)
        }
    }
}
