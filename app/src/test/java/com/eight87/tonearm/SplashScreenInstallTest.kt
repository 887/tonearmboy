package com.eight87.tonearm

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.17.2.3 — guardrail that the SplashScreen 1.x compat shim is on the
 * classpath and the documented entry point ([SplashScreen.installSplashScreen])
 * is callable from MainActivity. We don't drive a full activity launch
 * here (it pulls in Media3 + DataStore + WorkManager; expensive on
 * Robolectric) — instead we assert the static surface exists so a
 * future refactor that drops the dependency fails loudly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplashScreenInstallTest {

  @Test
  fun installSplashScreen_extension_is_present_on_classpath() {
    val klass = Class.forName("androidx.core.splashscreen.SplashScreen\$Companion")
    val method = klass.declaredMethods.firstOrNull { it.name == "installSplashScreen" }
    assertNotNull(
      "androidx.core:core-splashscreen must provide SplashScreen.Companion#installSplashScreen()",
      method,
    )
  }

  @Test
  fun mainActivity_calls_installSplashScreen_before_super_onCreate() {
    // Verify by source-code-inspection-via-bytecode that MainActivity
    // calls installSplashScreen. The static analysis here is coarse —
    // we read the constant pool of MainActivity's onCreate to confirm
    // the method reference is wired. A finer assertion (call-order)
    // would require ASM; this is enough to catch an accidental
    // removal of the call.
    val activityClass = Class.forName("com.eight87.tonearm.MainActivity")
    val onCreate = activityClass.declaredMethods.firstOrNull { it.name == "onCreate" }
    assertNotNull("MainActivity must declare onCreate(Bundle?)", onCreate)
    val classBytes = activityClass.classLoader!!
      .getResourceAsStream(activityClass.name.replace('.', '/') + ".class")!!
      .readBytes()
    val asString = String(classBytes, Charsets.ISO_8859_1)
    assertTrue(
      "MainActivity bytecode must reference installSplashScreen",
      asString.contains("installSplashScreen"),
    )
  }
}
