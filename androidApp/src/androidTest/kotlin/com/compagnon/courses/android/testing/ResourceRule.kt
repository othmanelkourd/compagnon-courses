package com.compagnon.courses.android.testing

import androidx.activity.ComponentActivity
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule

/** Expected texts come from the resources of the device locale, so the tests pass in French and in English. */
internal typealias ComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

internal fun ComposeRule.string(@StringRes id: Int, vararg args: Any): String = activity.getString(id, *args)

/** An overline (section, aisle) as displayed: in capitals, in the device locale. */
internal fun ComposeRule.overline(text: String): String = text.uppercase(activity.resources.configuration.locales[0])

internal fun ComposeRule.plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
    activity.resources.getQuantityString(id, count, *(if (args.isEmpty()) arrayOf<Any>(count) else args))
