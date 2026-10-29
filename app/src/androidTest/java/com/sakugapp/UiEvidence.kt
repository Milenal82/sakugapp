// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

/** Exports only the public/fixture screens explicitly captured by these tests. */
internal object UiEvidence {
    fun capture(name:String):File{
        require(name.matches(Regex("[a-z0-9-]+\\.png")))
        val instrumentation=InstrumentationRegistry.getInstrumentation();val device=UiDevice.getInstance(instrumentation)
        instrumentation.waitForIdleSync();device.waitForIdle();Thread.sleep(500)
        val directory=File(instrumentation.targetContext.getExternalFilesDir(null),"ui-evidence").apply{mkdirs()}
        val file=File(directory,name)
        check(device.takeScreenshot(file)){"Screenshot failed: writable=${directory.canWrite()}, package=${device.currentPackageName}"}
        // A shell-owned copy survives AGP's optional test-package uninstall.
        device.executeShellCommand("mkdir -p /sdcard/Download/sakugapp-ui-evidence")
        device.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/sakugapp-ui-evidence/$name")
        val bytes=device.executeShellCommand("stat -c %s /sdcard/Download/sakugapp-ui-evidence/$name").trim().toLongOrNull()
        check(bytes!=null && bytes>0){"Screenshot export failed: $name"}
        return file
    }
}
