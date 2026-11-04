package com.compagnon.courses.feature.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenSourceNoticesTest {

    @Test
    fun everyComponentHasTheTextOfItsLicense() {
        val ids = OpenSourceNotices.licenses.map { it.id }
        assertEquals(ids.distinct(), ids)
        OpenSourceNotices.components.forEach { assertTrue(it.licenseId in ids, it.name) }
        OpenSourceNotices.licenses.forEach { assertTrue(it.text.length > 500, it.id) }
    }

    @Test
    fun eachAppListsItsOwnComponentsGroupedByLicense() {
        val android = OpenSourceNotices.groupsFor(NoticePlatform.ANDROID)
        val ios = OpenSourceNotices.groupsFor(NoticePlatform.IOS)

        val androidNames = android.flatMap { group -> group.components.map { it.name } }
        val iosNames = ios.flatMap { group -> group.components.map { it.name } }
        assertTrue("Fluent Emoji" in androidNames && "Fluent Emoji" in iosNames)
        assertTrue("OkHttp, Okio" in androidNames && "OkHttp, Okio" !in iosNames)
        assertTrue("SKIE" in iosNames && "SKIE" !in androidNames)
        // The MIT license of SLF4J only ships with Android.
        assertTrue(ios.none { it.license.id == "mit-slf4j" })
        assertEquals(listOf("mit-fluent", "ofl-figtree", "ofl-bricolage", "apache-2.0"), ios.map { it.license.id })
        assertTrue(android.all { it.components.isNotEmpty() })
    }

    @Test
    fun theLicenseTextsAreComplete() {
        val apache = OpenSourceNotices.licenses.first { it.id == "apache-2.0" }.text
        assertTrue(apache.startsWith("Apache License"))
        assertTrue(apache.endsWith("END OF TERMS AND CONDITIONS"))
        val fluent = OpenSourceNotices.licenses.first { it.id == "mit-fluent" }.text
        assertTrue(fluent.startsWith("MIT License\n\nCopyright (c) Microsoft Corporation."))
    }
}
