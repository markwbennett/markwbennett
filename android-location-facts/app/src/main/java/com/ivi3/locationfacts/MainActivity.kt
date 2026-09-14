package com.ivi3.locationfacts

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivi3.locationfacts.location.LOCATION_PERMISSIONS
import com.ivi3.locationfacts.ui.FactsScreen
import com.ivi3.locationfacts.ui.theme.LocationFactsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: FactsViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        viewModel.onPermissionResult(grants.values.any { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LocationFactsTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                FactsScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onGrantPermission = { permissionLauncher.launch(LOCATION_PERMISSIONS) },
                    onSaveApiKey = viewModel::saveApiKey,
                    onForgetApiKey = viewModel::clearApiKey,
                    onOpenSource = ::openUrl,
                )
            }
        }

        // The whole point of the app: it does its work when you open it.
        viewModel.refresh()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No browser available to open that link", Toast.LENGTH_SHORT).show()
        }
    }
}
