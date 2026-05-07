package fail.tiger.komgarot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fail.tiger.komgarot.ui.navigation.AppNavGraph
import fail.tiger.komgarot.ui.theme.KomgarotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KomgarotApp
        enableEdgeToEdge()
        setContent {
            KomgarotTheme {
                AppNavGraph(app)
            }
        }
    }
}
