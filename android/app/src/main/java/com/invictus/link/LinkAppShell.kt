package com.invictus.link

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun InvictusAppShell(
    currentTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    pendingCount: Int,
    snackbarHostState: SnackbarHostState,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            InvictusBottomBar(
                currentTab = currentTab,
                onTabSelected = onTabSelected,
                pendingCount = pendingCount,
            )
        }
    ) { padding ->
        content(Modifier.padding(padding))
    }
}
