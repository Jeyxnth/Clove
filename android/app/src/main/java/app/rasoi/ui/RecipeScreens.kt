package app.rasoi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rasoi.R
import app.rasoi.data.Recipe
import app.rasoi.data.Scaler
import app.rasoi.sos.TapCounter

private val ART = mapOf(
    "Breakfast" to (Color(0xFFFFE0B2) to "🍳"),
    "Dal & Curries" to (Color(0xFFFFCCBC) to "🍛"),
    "Rice" to (Color(0xFFF5F5DC) to "🍚"),
    "Rotis & Breads" to (Color(0xFFFFF3C4) to "🥞"),
    "Snacks" to (Color(0xFFFFD6A5) to "🥟"),
    "Sweets" to (Color(0xFFF8BBD0) to "🍮"),
    "Chutneys" to (Color(0xFFC8E6C9) to "🌿"),
    "Beverages" to (Color(0xFFB3E5FC) to "🍵"),
)

/** Generated placeholder image: colour + emoji. No network images. */
@Composable
fun RecipeArt(category: String, modifier: Modifier = Modifier, emojiSize: Int = 40) {
    val (bg, emoji) = ART[category] ?: (Color(0xFFEEEEEE) to "🍽")
    Box(modifier.background(bg), contentAlignment = Alignment.Center) { Text(emoji, fontSize = emojiSize.sp) }
}

@Composable
private fun RecipeCard(r: Recipe, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RecipeArt(r.category, Modifier.size(64.dp).background(Color.Transparent, RoundedCornerShape(8.dp)))
            Column(Modifier.padding(start = 12.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium)
                Text(r.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Text(stringResource(R.string.minutes, r.timeMinutes), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RecipeList(list: List<Recipe>, pad: PaddingValues, onRecipe: (String) -> Unit, empty: String) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
            Text(empty, textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad.plus(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.id }) { RecipeCard(it, { onRecipe(it.id) }) }
    }
}

private fun PaddingValues.plus(extra: androidx.compose.ui.unit.Dp) = PaddingValues(
    start = extra, end = extra, top = calculateTopPadding() + extra, bottom = calculateBottomPadding() + extra,
)

@Composable
fun HomeScreen(vm: RecipeViewModel, pad: PaddingValues, onCategory: (String) -> Unit, onRecipe: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad.plus(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Text(stringResource(R.string.featured), style = MaterialTheme.typography.titleMedium) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(vm.repo.featured, key = { it.id }) { r ->
                    Card(Modifier.width(160.dp).clickable { onRecipe(r.id) }) {
                        RecipeArt(r.category, Modifier.fillMaxWidth().height(90.dp))
                        Text(r.title, Modifier.padding(8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    }
                }
            }
        }
        item { Text(stringResource(R.string.categories), style = MaterialTheme.typography.titleMedium) }
        items(vm.repo.categories) { c ->
            Card(Modifier.fillMaxWidth().clickable { onCategory(c) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RecipeArt(c, Modifier.size(48.dp), emojiSize = 28)
                    Text(c, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: RecipeViewModel, pad: PaddingValues, query: String, onQuery: (String) -> Unit, onRecipe: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())) {
        OutlinedTextField(
            query, onQuery, Modifier.fillMaxWidth().padding(16.dp), singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            keyboardOptions = KeyboardOptions.Default,
        )
        RecipeList(vm.repo.search(query), PaddingValues(), onRecipe, stringResource(R.string.no_results))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        actions = { actions() },
    )
}

@Composable
fun CategoryScreen(vm: RecipeViewModel, category: String, onBack: () -> Unit, onRecipe: (String) -> Unit) {
    Column {
        BackBar(category, onBack)
        RecipeList(vm.repo.inCategory(category), PaddingValues(), onRecipe, stringResource(R.string.no_results))
    }
}

@Composable
fun FavouritesScreen(vm: RecipeViewModel, pad: PaddingValues, favourites: Set<String>, onRecipe: (String) -> Unit) {
    RecipeList(vm.repo.all.filter { it.id in favourites }, pad, onRecipe, stringResource(R.string.no_favourites))
}

/**
 * [onHeartTrigger] is called when the tap pattern completes. The favourite is then put back to what it
 * was before the pattern began, so nothing visible changes except the navigation.
 */
@Composable
fun RecipeDetailScreen(
    vm: RecipeViewModel, recipe: Recipe, favourites: Set<String>, millilitres: Boolean,
    onBack: () -> Unit, onHeartTrigger: () -> Unit,
) {
    val isFav = recipe.id in favourites
    val counter = remember(recipe.id) { TapCounter() }
    DisposableEffect(recipe.id) { onDispose { counter.reset() } }
    var servings by rememberSaveable(recipe.id) { mutableIntStateOf(recipe.servings) }

    Column(Modifier.fillMaxSize()) {
        BackBar(recipe.title, onBack) {
            IconButton(onClick = {
                val r = counter.onTap(recipe.id, isFav, System.currentTimeMillis())
                if (r.triggered) {
                    vm.setFavourite(recipe.id, r.favouriteBeforeSequence)
                    onHeartTrigger()
                } else vm.toggleFavourite(recipe.id)
            }) {
                Icon(
                    if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    stringResource(if (isFav) R.string.fav_remove else R.string.fav_add),
                    tint = if (isFav) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                RecipeArt(recipe.category, Modifier.fillMaxWidth().height(140.dp).background(Color.Transparent, RoundedCornerShape(12.dp)), 64)
                Text(recipe.description, Modifier.padding(top = 8.dp))
                Text(recipe.category + "  •  " + stringResource(R.string.minutes, recipe.timeMinutes), style = MaterialTheme.typography.labelMedium)
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.servings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton({ if (servings > 1) servings-- }) { Icon(Icons.Filled.Remove, stringResource(R.string.decrease)) }
                    Text("$servings", style = MaterialTheme.typography.titleMedium)
                    IconButton({ if (servings < 24) servings++ }) { Icon(Icons.Filled.Add, stringResource(R.string.increase)) }
                }
            }
            item { Text(stringResource(R.string.ingredients), style = MaterialTheme.typography.titleMedium) }
            items(recipe.ingredients) { ing ->
                val (q, unit) = Scaler.display(Scaler.scale(ing.quantity, recipe.servings, servings), ing.unit, millilitres)
                Text("• ${Scaler.format(q)} $unit  ${ing.name}")
            }
            item { Text(stringResource(R.string.steps), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            itemsIndexed(recipe.steps) { i, s -> Text("${i + 1}. $s") }
        }
    }
}

@Composable
fun SettingsScreen(vm: RecipeViewModel, pad: PaddingValues, theme: String, millilitres: Boolean) {
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
        listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark).forEach { (key, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(theme == key, onClick = { vm.setTheme(key) }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(theme == key, null)
                Text(stringResource(label), Modifier.padding(start = 8.dp))
            }
        }
        Text(stringResource(R.string.settings_units), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.units_ml), Modifier.weight(1f))
            Switch(millilitres, { vm.setMillilitres(it) })
        }
        Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(stringResource(R.string.about_text))
    }
}
