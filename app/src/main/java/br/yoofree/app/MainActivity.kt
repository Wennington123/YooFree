package br.yoofree.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import br.yoofree.app.ui.Bg
import br.yoofree.app.ui.YooFreeApp
import br.yoofree.app.ui.YooFreeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YooFreeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    YooFreeApp()
                }
            }
        }
    }
}
