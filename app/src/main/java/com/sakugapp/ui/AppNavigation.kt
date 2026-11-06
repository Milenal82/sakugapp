// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.sakugapp.R
import com.sakugapp.data.LocalProfile

object AppRoutes {const val SEARCH="search_home";const val POSTS="posts";const val PROFILE="profile";val main=setOf(SEARCH,POSTS,PROFILE)}
@Composable internal fun RowScope.BrandAndProfile(profile:LocalProfile,showProfile:Boolean,open:()->Unit){
    val word=buildAnnotatedString{withStyle(SpanStyle(color=MaterialTheme.colorScheme.onSurface)){append("SAKUG")};withStyle(SpanStyle(color=if(MaterialTheme.colorScheme.background.luminance()>.5f)Color(0xFFC62828)else Color(0xFFFF5252))){append("APP")}}
    Text(word,fontSize=18.sp,maxLines=1,modifier=Modifier.testTag("brand-logo"))
    Spacer(Modifier.width(8.dp))
    if(showProfile)Row(Modifier.weight(1f).widthIn(max=140.dp).clickable(onClick=open).padding(vertical=4.dp).testTag("top-profile"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
        LocalAvatar(profile,30);Text(profile.nickname ?: text(R.string.ui_local_user),style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,false))
    }else Spacer(Modifier.weight(1f))
}
@Composable internal fun MainBottomBar(route:String,select:(String)->Unit){
    Column(Modifier.navigationBarsPadding().testTag("main-bottom-nav")){
        NavigationBar(modifier=Modifier.height(72.dp),windowInsets=WindowInsets(0,0,0,0)){
            listOf(Triple(AppRoutes.SEARCH,R.drawable.ic_search,R.string.ui_search),Triple(AppRoutes.POSTS,R.drawable.ic_book,R.string.ui_posts),Triple(AppRoutes.PROFILE,R.drawable.ic_profile,R.string.ui_profile)).forEach{(target,icon,label)->
                NavigationBarItem(colors=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary,indicatorColor=MaterialTheme.colorScheme.primaryContainer),selected=route==target,onClick={select(target)},icon={Icon(painterResource(icon),null,Modifier.size(24.dp))},label={Text(text(label),maxLines=1)},modifier=Modifier.testTag("nav-$target"))
            }
        }
    }
}
