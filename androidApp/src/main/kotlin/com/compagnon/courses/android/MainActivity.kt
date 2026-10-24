package com.compagnon.courses.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.compagnon.courses.android.catalog.CatalogStatusScreen
import com.compagnon.courses.android.ui.theme.CompagnonTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CompagnonTheme {
                CatalogStatusScreen()
            }
        }
    }
}
