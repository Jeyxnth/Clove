package app.rasoi

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.rasoi.data.RecipeRepository
import app.rasoi.data.RecipeStore
import app.rasoi.sos.LocationSource
import app.rasoi.sos.ReportApi
import app.rasoi.sos.SmsBackend
import app.rasoi.sos.SosPrefs
import app.rasoi.sos.SosViewModel
import app.rasoi.ui.RecipeViewModel
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class RasoiApp : Application() {
    val container by lazy { AppContainer(this) }
}

/** Manual dependency wiring (no DI framework). */
class AppContainer(private val context: Context) {
    private val recipes by lazy { RecipeRepository(context) }
    private val store by lazy { RecipeStore(context) }

    private val api: ReportApi by lazy {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).writeTimeout(8, TimeUnit.SECONDS)
            .build() // no cache, no logging interceptor: nothing about a report is kept or logged
        Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(ReportApi::class.java)
    }

    val recipeVmFactory = viewModelFactory { initializer { RecipeViewModel(recipes, store) } }
    val sosVmFactory = viewModelFactory {
        initializer {
            SosViewModel(SosPrefs(context), api, LocationSource(context)) { to, text, cb -> SmsBackend.send(context, to, text, cb) }
        }
    }
}
