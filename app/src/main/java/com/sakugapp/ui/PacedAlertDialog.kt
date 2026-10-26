// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider

/** Native dialog with a cancellable 280 ms enter/exit; Compose uses Android's duration scale. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PacedAlertDialog(visible:Boolean,onDismissRequest:()->Unit,title:@Composable () -> Unit,
    text:@Composable () -> Unit,confirmButton:@Composable () -> Unit,dismissButton:@Composable () -> Unit={}){
    val transition=remember{MutableTransitionState(false)}
    LaunchedEffect(visible){transition.targetState=visible}
    if(transition.currentState || transition.targetState)Dialog(onDismissRequest=onDismissRequest){
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect{window?.setWindowAnimations(0)}
        AnimatedVisibility(transition,enter=fadeIn(tween(Motion.folder))+scaleIn(tween(Motion.folder),initialScale=0.96f),exit=fadeOut(tween(Motion.folder))+scaleOut(tween(Motion.folder),targetScale=0.96f)){
            Surface(shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.surfaceContainerHigh){
                Column(Modifier.widthIn(min=280.dp,max=560.dp).heightIn(max=600.dp).padding(24.dp)){
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall,title)
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.weight(1f,fill=false)){ProvideTextStyle(MaterialTheme.typography.bodyMedium,text)}
                    Spacer(Modifier.height(20.dp))
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){dismissButton();confirmButton()}
                }
            }
        }
    }
}
