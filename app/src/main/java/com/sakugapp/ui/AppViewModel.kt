// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sakugapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppViewModel(application:Application):AndroidViewModel(application) {
    val preferences=PreferencesStore(application)
    val settings=preferences.settings.stateIn(viewModelScope,SharingStarted.Eagerly,AppSettings())
    val history=preferences.history.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val session=SecureSession(application)
    val suggestions:SuggestionSource=TagSuggestionRepository()
    val community=CommunityRepository(client=OkHttpClient.Builder().cookieJar(session).callTimeout(30,TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false).build())
    private val _account=MutableStateFlow<Account?>(null);val account=_account.asStateFlow()
    private val _authBusy=MutableStateFlow(false);val authBusy=_authBusy.asStateFlow()
    private val _authError=MutableStateFlow<String?>(null);val authError=_authError.asStateFlow()
    init {
        viewModelScope.launch { _account.value=withContext(Dispatchers.IO){session.restore()} }
        viewModelScope.launch { settings.collect { community.language=it.language } }
    }
    fun search(query:String) { viewModelScope.launch { preferences.remember(query) } }
    fun flag(name:String,value:Boolean) { viewModelScope.launch { preferences.flag(name,value) } }
    fun number(name:String,value:Float) { viewModelScope.launch { preferences.number(name,value) } }
    fun language(value:String) { viewModelScope.launch { LocaleCoordinator.choose(getApplication(),value,preferences) } }
    fun clearHistory() { viewModelScope.launch { preferences.clearHistory() } }
    fun deleteHistory(query:String) { viewModelScope.launch { preferences.deleteHistory(query) } }
    fun columns(value:Int) { viewModelScope.launch { preferences.columns(value) } }
    fun login(name:String,password:String) {
        if(_authBusy.value)return
        _authBusy.value=true;_authError.value=null
        viewModelScope.launch {
            try { val result=community.login(name.trim(),password);withContext(Dispatchers.IO){session.confirm(result)};_account.value=result }
            catch(e:CancellationException){throw e}
            catch(e:Exception){_authError.value=e.message ?: "Login failed."}
            finally{_authBusy.value=false}
        }
    }
    fun syncBrowserSession() {
        val header=android.webkit.CookieManager.getInstance().getCookie("https://www.sakugabooru.com")
        viewModelScope.launch {
            _authBusy.value=true;_account.value=null;_authError.value=null
            try {
                withContext(Dispatchers.IO){session.importBrowser(header)}
                val user=community.identity()
                if(user!=null)withContext(Dispatchers.IO){session.confirm(user)}
                _account.value=user
            } catch(e:CancellationException){throw e} catch(e:Exception){_authError.value=e.message ?: "Unable to verify the website session."}
            finally{_authBusy.value=false}
        }
    }
    fun logout() { viewModelScope.launch { withContext(Dispatchers.IO){session.clear()};_account.value=null;_authError.value=null
        android.webkit.CookieManager.getInstance().removeAllCookies { android.webkit.CookieManager.getInstance().flush() }
    } }
}
