package app.rasoi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rasoi.data.RecipeRepository
import app.rasoi.data.RecipeStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecipeViewModel(val repo: RecipeRepository, private val store: RecipeStore) : ViewModel() {
    val favourites: StateFlow<Set<String>> = store.favourites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val theme: StateFlow<String> = store.theme.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val millilitres: StateFlow<Boolean> = store.millilitres.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleFavourite(id: String) { viewModelScope.launch { store.toggleFavourite(id) } }
    fun setFavourite(id: String, on: Boolean) { viewModelScope.launch { store.setFavourite(id, on) } }
    fun setTheme(v: String) { viewModelScope.launch { store.setTheme(v) } }
    fun setMillilitres(v: Boolean) { viewModelScope.launch { store.setMillilitres(v) } }
}
