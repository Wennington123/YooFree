package br.yoofree.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import br.yoofree.app.ui.Bg
import br.yoofree.app.ui.ThemeMode
import br.yoofree.app.ui.YooFreeApp
import br.yoofree.app.ui.YooFreeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("yoofree", Context.MODE_PRIVATE)
        setContent {
            var themeMode by remember {
                mutableStateOf(ThemeMode.fromId(prefs.getString("theme", null)))
            }
            YooFreeTheme(themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    YooFreeApp(
                        onThemeMode = { mode ->
                            prefs.edit().putString("theme", mode.id).apply()
                            themeMode = mode
                        },
                    )
                }
            }
        }
    }
}
