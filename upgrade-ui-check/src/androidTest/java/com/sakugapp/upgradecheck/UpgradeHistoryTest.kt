// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.upgradecheck

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Self-instrumented helper interacts with the real signed app through native UI only. */
@RunWith(AndroidJUnit4::class)
class UpgradeHistoryTest {
    @Test fun signedAppSearchOpensAndOldHistoryIsVisible(){
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        val entry=device.wait(Until.findObject(By.res("search-entry")),20000)
        assertNotNull("Signed app search entry",entry)
        assertTrue("Search entry clickable",entry.isClickable)
        entry.click(100L)
        val field=device.wait(Until.findObject(By.res("search-input").focused(true)),15000)
        assertNotNull("Signed app search field automatically focused",field)
        val history=device.wait(Until.findObject(By.text("History")),15000)
        assertNotNull("History action",history);history.click(100L)
        val old=device.wait(Until.findObject(By.text("id:319947").hasAncestor(By.res("search-history"))),15000)
        assertNotNull("History from the published APK survived upgrade",old)
    }
}
