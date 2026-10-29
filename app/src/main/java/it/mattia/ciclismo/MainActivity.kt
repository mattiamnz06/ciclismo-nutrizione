package it.mattia.ciclismo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import it.mattia.ciclismo.data.Repo
import it.mattia.ciclismo.ui.CiclismoApp
import it.mattia.ciclismo.ui.CiclismoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        enableEdgeToEdge()
        handle(intent)
        setContent { CiclismoTheme { CiclismoApp() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Strava redirects to ciclismo://strava?code=... after the user approves. */
    private fun handle(i: Intent?) {
        val uri = i?.data ?: return
        if (uri.scheme == "ciclismo" && uri.host == "strava") {
            uri.getQueryParameter("code")?.let { Repo.stravaCode = it }
        }
    }
}
