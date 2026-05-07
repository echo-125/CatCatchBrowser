package top.he2000.catcatchbrowser.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.he2000.catcatchbrowser.ui.theme.CatCatchBrowserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CatCatchBrowserTheme {
                MainScreen()
            }
        }
    }
}
