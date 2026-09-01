package com.example

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Study Assistant AI", appName)
  }

  @Test
  fun `launch MainActivity without crash`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java)
    controller.create().start().resume()
    assertNotNull(controller.get())
  }

  @Test
  fun `verify all language strings load without error`() {
    val languages = com.example.ui.localization.AppLanguages.SUPPORTED
    for (lang in languages) {
      val strings = com.example.ui.localization.LocalizationHelper.getStrings(lang.code)
      assertNotNull(strings)
      assertNotNull(strings.navHome)
      assertNotNull(strings.aiChat)
      assertNotNull(strings.welcomePrefix)
    }
  }
}
