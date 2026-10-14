package app.rasoi.sos

import app.rasoi.Config

/**
 * Counts taps on one recipe's heart. In-memory only.
 * The window runs from the first tap of a sequence, so a pause longer than the window also restarts it.
 */
class TapCounter(
    private val taps: Int = Config.TRIGGER_TAPS,
    private val windowMs: Long = Config.TRIGGER_WINDOW_MS,
) {
    /** [favouriteBeforeSequence] is what the caller should restore when [triggered]. */
    data class Result(val triggered: Boolean, val favouriteBeforeSequence: Boolean)

    private var recipeId: String? = null
    private var startedFavourite = false
    private var count = 0
    private var firstAt = 0L

    fun onTap(recipeId: String, favouriteNow: Boolean, nowMs: Long): Result {
        if (count == 0 || this.recipeId != recipeId || nowMs - firstAt > windowMs) {
            this.recipeId = recipeId
            startedFavourite = favouriteNow
            firstAt = nowMs
            count = 0
        }
        count++
        val result = Result(count >= taps, startedFavourite)
        if (result.triggered) reset()
        return result
    }

    fun reset() {
        recipeId = null
        count = 0
    }
}
