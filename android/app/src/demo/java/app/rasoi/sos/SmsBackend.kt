package app.rasoi.sos

import android.content.Context

/** demo flavor: no SMS capability and no SEND_SMS permission. Same API as the sms flavor. */
object SmsBackend {
    const val enabled = false
    val permissions = emptyArray<String>()
    val relayNumber = ""

    @Suppress("UNUSED_PARAMETER")
    fun hasPermission(context: Context) = false

    @Suppress("UNUSED_PARAMETER")
    fun send(context: Context, to: String, text: String, onEvent: (SmsEvent) -> Unit) = onEvent(SmsEvent.FAILED)
}
