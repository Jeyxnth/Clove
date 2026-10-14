package app.rasoi.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

@Serializable
data class Ingredient(val name: String, val quantity: Double, val unit: String)

@Serializable
data class Recipe(
    val id: String,
    val title: String,
    val category: String,
    val timeMinutes: Int,
    val servings: Int,
    val description: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
)

class RecipeRepository(context: Context) {
    val all: List<Recipe> = context.assets.open("recipes.json").bufferedReader().use {
        Json.decodeFromString(it.readText())
    }
    val categories: List<String> = all.map { it.category }.distinct()
    val featured: List<Recipe> = all.take(6)

    fun byId(id: String) = all.firstOrNull { it.id == id }
    fun inCategory(c: String) = all.filter { it.category == c }
    fun search(q: String): List<Recipe> {
        val s = q.trim().lowercase()
        if (s.isEmpty()) return emptyList()
        return all.filter { r -> s in r.title.lowercase() || r.ingredients.any { s in it.name.lowercase() } }
    }
}

object Scaler {
    fun scale(quantity: Double, baseServings: Int, servings: Int): Double = quantity * servings / baseServings

    private val ML = mapOf("tsp" to 5.0, "tbsp" to 15.0, "cup" to 240.0)

    /** Optionally shows spoon/cup measures as millilitres. */
    fun display(quantity: Double, unit: String, millilitres: Boolean): Pair<Double, String> =
        if (millilitres && unit in ML) quantity * ML.getValue(unit) to "ml" else quantity to unit

    /** 0.5 -> "½", 2.25 -> "2¼", 3.0 -> "3", 0.1 -> "0.1", 45.2 -> "45". */
    fun format(q: Double): String {
        if (q >= 20) return q.roundToInt().toString()
        val whole = floor(q).toInt()
        val frac = q - whole
        val glyph = listOf(0.0 to "", 0.25 to "¼", 1.0 / 3 to "⅓", 0.5 to "½", 2.0 / 3 to "⅔", 0.75 to "¾", 1.0 to "")
            .firstOrNull { abs(frac - it.first) < 0.04 }
        if (glyph != null) {
            val w = if (glyph.first == 1.0) whole + 1 else whole
            return if (w == 0 && glyph.second.isNotEmpty()) glyph.second else if (w == 0) "0" else "$w${glyph.second}"
        }
        return "%.1f".format(java.util.Locale.ROOT, q).removeSuffix(".0")
    }
}
