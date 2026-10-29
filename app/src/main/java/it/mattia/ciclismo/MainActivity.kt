package it.mattia.ciclismo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import it.mattia.ciclismo.ui.CiclismoApp
import it.mattia.ciclismo.ui.CiclismoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CiclismoTheme { CiclismoApp(applicationContext) } }
    }
}
