// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.sakugapp.data.PreferencesStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DetailTranslationSettingsTest {
    @Test fun translationDefaultsOffAndSurvivesClosingAndReopeningDataStore()=runBlocking {
        val directory=Files.createTempDirectory("sakugabot-settings").toFile()
        val file=directory.resolve("settings.preferences_pb")
        suspend fun session(block:suspend (PreferencesStore)->Unit){
            val job=SupervisorJob();val scope=CoroutineScope(job+Dispatchers.IO)
            val data=PreferenceDataStoreFactory.create(scope=scope,produceFile={file})
            try{block(PreferencesStore(data))}finally{job.cancelAndJoin()}
        }
        try{
            session{store->
                assertFalse(store.settings.first().chineseDetailTranslations)
                store.remember("artist_tag order:score");store.columns(6);store.chineseDetailTranslations(true)
                assertTrue(store.settings.first().chineseDetailTranslations)
            }
            session{store->
                store.initializeTheme()
                assertTrue(store.settings.first().chineseDetailTranslations)
                assertEquals(6,store.settings.first().columns)
                assertEquals(listOf("artist_tag order:score"),store.history.first())
                store.chineseDetailTranslations(false)
            }
            session{assertFalse(it.settings.first().chineseDetailTranslations)}
        }finally{directory.deleteRecursively()}
    }
    @Test fun removedLegacyTranslationFlagCannotOptUserIn()=runBlocking {
        val directory=Files.createTempDirectory("sakugabot-legacy").toFile()
        val job=SupervisorJob();val data=PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={directory.resolve("settings.preferences_pb")})
        try{
            data.edit{it[booleanPreferencesKey("tagTranslations")]=true}
            val store=PreferencesStore(data)
            assertFalse(store.settings.first().chineseDetailTranslations)
            store.initializeTheme()
            assertFalse(store.settings.first().chineseDetailTranslations)
            assertNull(data.data.first()[booleanPreferencesKey("tagTranslations")])
        }finally{job.cancelAndJoin();directory.deleteRecursively()}
    }
}
