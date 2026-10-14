package app.rasoi.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("recipe_prefs")

/** Recipe-side persistence only: favourites and display settings. */
class RecipeStore(private val context: Context) {
    private val favKey = stringSetPreferencesKey("favourites")
    private val themeKey = stringPreferencesKey("theme")
    private val mlKey = booleanPreferencesKey("ml")

    val favourites: Flow<Set<String>> = context.dataStore.data.map { it[favKey] ?: emptySet() }
    val theme: Flow<String> = context.dataStore.data.map { it[themeKey] ?: "system" }
    val millilitres: Flow<Boolean> = context.dataStore.data.map { it[mlKey] ?: false }

    suspend fun setFavourite(id: String, on: Boolean) {
        context.dataStore.edit { p -> p[favKey] = (p[favKey] ?: emptySet()).let { if (on) it + id else it - id } }
    }

    suspend fun toggleFavourite(id: String) {
        context.dataStore.edit { p ->
            val cur = p[favKey] ?: emptySet()
            p[favKey] = if (id in cur) cur - id else cur + id
        }
    }

    suspend fun setTheme(v: String) = context.dataStore.edit { it[themeKey] = v }.let { }
    suspend fun setMillilitres(v: Boolean) = context.dataStore.edit { it[mlKey] = v }.let { }
}
