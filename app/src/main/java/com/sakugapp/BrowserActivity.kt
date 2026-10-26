// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Used only for official account management, Blog and Forum. Posts always return to native UI. */
class BrowserActivity:androidx.appcompat.app.AppCompatActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val target=intent.getStringExtra("url") ?: return finish()
        if(!UrlPolicy.trusted(target) || UrlPolicy.isPosts(target) || UrlPolicy.postId(target)!=null)return finish()
        setContent {
            var language by remember{mutableStateOf("en")}
            var lightTheme by remember{mutableStateOf(false)}
            var ready by remember{mutableStateOf(false)}
            var browser by remember{mutableStateOf<WebView?>(null)}
            var error by remember{mutableStateOf<String?>(null)}
            var loading by remember{mutableStateOf(true)}
            LaunchedEffect(Unit) {
                val preferences=PreferencesStore(applicationContext).settings.first()
                language=preferences.language;lightTheme=themeIsLight(preferences.themeMode,(resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES)
                val cookies=withContext(Dispatchers.IO){SecureSession(applicationContext).apply{restore()}.browserCookies()}
                val manager=CookieManager.getInstance();manager.setAcceptCookie(true)
                for(cookie in cookies)withContext(Dispatchers.Main){suspendCancellableCoroutine<Unit>{continuation->manager.setCookie("https://www.sakugabooru.com",cookie){continuation.resumeWith(Result.success(Unit))}}}
                manager.flush();ready=true;setResult(Activity.RESULT_OK)
            }
            DisposableEffect(Unit){onDispose{browser?.stopLoading();browser?.destroy();browser=null}}
            CompositionLocalProvider(LocalLanguage provides language){SakugappTheme(lightTheme){
                BackHandler{if(browser?.canGoBack()==true)browser?.goBack()else finish()}
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()){
                    Row{
                        TextButton(onClick={finish()}){Text(text(R.string.ui_back_to_app))}
                        TextButton(onClick={browser?.reload();error=null}){Text(text(R.string.ui_reload))}
                    }
                    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let{Text(translateMessage(it),color=MaterialTheme.colorScheme.error)}
                    if(ready)AndroidView(factory={context->WebView(context).apply{
                        browser=this
                        settings.javaScriptEnabled=true;settings.domStorageEnabled=true
                        settings.allowFileAccess=false;settings.allowContentAccess=false
                        settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
                        webViewClient=object:WebViewClient(){
                            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                                val url=request.url.toString();val id=UrlPolicy.postId(url)
                                if(id!=null || UrlPolicy.isPosts(url)){
                                    setResult(Activity.RESULT_OK,Intent().apply{if(id!=null)putExtra("postId",id)else putExtra("query",UrlPolicy.tags(url))});finish();return true
                                }
                                if(UrlPolicy.trusted(url))return false
                                if(PostParser.safeUrl(url)!=null)runCatching{startActivity(Intent(Intent.ACTION_VIEW,request.url))}
                                return true
                            }
                            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? {
                                if(request.isForMainFrame && !UrlPolicy.trusted(request.url.toString()))return WebResourceResponse("text/plain","UTF-8",403,"Blocked",emptyMap(),java.io.ByteArrayInputStream("Navigation blocked".toByteArray()))
                                return null
                            }
                            override fun onPageStarted(view:WebView,url:String,favicon:android.graphics.Bitmap?){loading=true;error=null}
                            override fun onPageFinished(view:WebView,url:String){loading=false;CookieManager.getInstance().flush()}
                            override fun onReceivedError(view:WebView,request:WebResourceRequest,failure:WebResourceError){if(request.isForMainFrame){loading=false;error=view.context.getString(R.string.ui_page_load_failed,failure.errorCode.toString())}}
                        }
                        loadUrl(UrlPolicy.localized(target,language),mapOf("Accept-Language" to PreferenceRules.acceptLanguage(language)))
                    }},modifier=Modifier.fillMaxWidth().weight(1f))
                }
            }}
        }
    }
}
