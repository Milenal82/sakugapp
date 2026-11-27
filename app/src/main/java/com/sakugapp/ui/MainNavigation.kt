// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/** Main UI saveable state is held separately; never restore a child stack then immediately pop it. */
internal fun navigatePrimary(nav:NavHostController,route:String){
    require(route in AppRoutes.main)
    if(nav.currentDestination?.route==route)return
    nav.navigate(route){popUpTo(nav.graph.startDestinationId){saveState=false};launchSingleTop=true;restoreState=false}
}

/** Release the bar's space once its exit settles, rather than remeasuring the grid every frame.
 * The system navigation inset remains reserved; entry reserves the bar before its slide begins.
 */
@Composable internal fun MainNavigationChrome(route:String?,visible:Boolean,select:(String)->Unit){
    if(route !in AppRoutes.main)return
    val visibility=remember{MutableTransitionState(visible)}
    visibility.targetState=visible
    val inset=with(LocalDensity.current){WindowInsets.navigationBars.getBottom(this).toDp()}
    val height=inset+if(visibility.currentState || visibility.targetState)72.dp else 0.dp
    Box(Modifier.fillMaxWidth().height(height).clipToBounds().testTag("main-nav-slot")){
        AnimatedVisibility(visibility,enter=slideInVertically(tween(190)){it}+fadeIn(tween(190)),
            exit=slideOutVertically(tween(170)){it}+fadeOut(tween(170))){MainBottomBar(route!!,select)}
    }
}
