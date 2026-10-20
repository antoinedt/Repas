package com.antoinedt.dinners

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.antoinedt.dinners.ui.DinnersApp
import com.antoinedt.dinners.ui.DinnersTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DinnersViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            DinnersTheme {
                DinnersApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) viewModel.onShared(intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
