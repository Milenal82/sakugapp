// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/** Main UI saveable state is held separately; never restore a child stack then immediately pop it. */
internal fun navigatePrimary(nav:NavHostController,route:String){
    require(route in AppRoutes.main)
    if(nav.currentDestination?.route==route)return
    nav.navigate(route){popUpTo(nav.graph.startDestinationId){saveState=false};launchSingleTop=true;restoreState=false}
}

/** Fixed space while scrolling: translation/fade never resizes Scaffold or the grid viewport. */
@Composable internal fun MainNavigationChrome(route:String?,visible:Boolean,select:(String)->Unit){
    if(route !in AppRoutes.main)return
    val height=72.dp+with(LocalDensity.current){WindowInsets.navigationBars.getBottom(this).toDp()}
    Box(Modifier.fillMaxWidth().height(height).clipToBounds()){
        AnimatedVisibility(visible,enter=slideInVertically(tween(Motion.chrome)){it}+fadeIn(tween(Motion.chrome)),
            exit=slideOutVertically(tween(Motion.chrome)){it}+fadeOut(tween(Motion.chrome))){MainBottomBar(route!!,select)}
    }
}
