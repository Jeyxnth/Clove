package app.rasoi

/** All tunables in one place. Trigger values are placeholders pending usability / accidental-activation testing. */
object Config {
    const val TRIGGER_TAPS = 5
    const val TRIGGER_WINDOW_MS = 10_000L

    const val MAX_MESSAGE_CHARS = 280
    /** Delays before retry 1, 2, 3 (after the first attempt). Only while the report screen stays open. */
    val RETRY_BACKOFF_SECONDS = longArrayOf(2, 5, 10)
    const val STATUS_POLL_MS = 5_000L
    const val SMS_OFFER_AFTER_MS = 10_000L
    const val LOCATION_TIMEOUT_MS = 5_000L

    // TODO(config): recipient and AI provider labels for the production flow.
    const val RECIPIENT_LABEL = "Demo responder dashboard (simulated)"
    const val AI_PROVIDER_LABEL = "local demo model"

    const val DEFAULT_CONTACT_TEXT = "Please check in with me when you can."
}
