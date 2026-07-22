package com.example.ui.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.AuraScreen

@Composable
fun AuraBottomNavigation(
    currentScreen: AuraScreen,
    onNavigate: (AuraScreen) -> Unit
) {
    val navColors = NavigationBarItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    NavigationBar(
        modifier = Modifier
            .navigationBarsPadding()
            .testTag("bottom_navigation_bar"),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        NavigationBarItem(
            selected = currentScreen == AuraScreen.HOME,
            onClick = { onNavigate(AuraScreen.HOME) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == AuraScreen.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Home"
                )
            },
            label = { Text("Home") },
            modifier = Modifier.testTag("nav_home"),
            colors = navColors
        )

        NavigationBarItem(
            selected = currentScreen == AuraScreen.EXPLORE,
            onClick = { onNavigate(AuraScreen.EXPLORE) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == AuraScreen.EXPLORE) Icons.Filled.Explore else Icons.Outlined.Explore,
                    contentDescription = "Explore"
                )
            },
            label = { Text("Explore") },
            modifier = Modifier.testTag("nav_explore"),
            colors = navColors
        )

        NavigationBarItem(
            selected = currentScreen == AuraScreen.CREATE,
            onClick = { onNavigate(AuraScreen.CREATE) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == AuraScreen.CREATE) Icons.Filled.AddBox else Icons.Outlined.AddBox,
                    contentDescription = "Create"
                )
            },
            label = { Text("Create") },
            modifier = Modifier.testTag("nav_create"),
            colors = navColors
        )

        NavigationBarItem(
            selected = currentScreen == AuraScreen.REELS,
            onClick = { onNavigate(AuraScreen.REELS) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == AuraScreen.REELS) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                    contentDescription = "Reels"
                )
            },
            label = { Text("Reels") },
            modifier = Modifier.testTag("nav_reels"),
            colors = navColors
        )

        NavigationBarItem(
            selected = currentScreen == AuraScreen.PROFILE,
            onClick = { onNavigate(AuraScreen.PROFILE) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == AuraScreen.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "Profile"
                )
            },
            label = { Text("Profile") },
            modifier = Modifier.testTag("nav_profile"),
            colors = navColors
        )
    }
}
