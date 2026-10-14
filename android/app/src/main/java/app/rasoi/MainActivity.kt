package app.rasoi

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.NavHostController
import app.rasoi.sos.SosViewModel
import app.rasoi.ui.AppRoot
import app.rasoi.ui.RecipeViewModel
import app.rasoi.ui.goHome

class MainActivity : ComponentActivity() {
    private val container get() = (application as RasoiApp).container
    private val recipeVm: RecipeViewModel by viewModels { container.recipeVmFactory }
    private val sosVm: SosViewModel by viewModels { container.sosVmFactory }
    private var nav: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot(recipeVm, sosVm, { nav = it }, ::setSecure) }
    }

    private fun setSecure(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** Leaving the app (home, app switch, dialer) clears the report session and returns to recipe Home. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && sosVm.ui.value.sessionOpen) {
            sosVm.wipe()
            nav?.goHome()
        }
    }
}
