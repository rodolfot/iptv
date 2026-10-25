package com.iptv.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun newerMajorMinorPatch() {
        assertTrue(AppVersion.isNewer("4.0.0", "3.1.0"))
        assertTrue(AppVersion.isNewer("v3.2.0", "3.1.9"))
        assertTrue(AppVersion.isNewer("3.1.1", "3.1.0"))
        assertTrue(AppVersion.isNewer("3.10.0", "3.9.0"))
    }

    @Test
    fun sameOrOlderIsNotNewer() {
        assertFalse(AppVersion.isNewer("3.1.0", "3.1.0"))
        assertFalse(AppVersion.isNewer("v3.1.0", "3.1.0"))
        assertFalse(AppVersion.isNewer("3.0.0", "3.1.0"))
    }

    @Test
    fun localGitDescribeBuildsCompareByCore() {
        // `git describe` em build local: 4.0.0-3-gabc123-dirty ainda é a 4.0.0.
        assertFalse(AppVersion.isNewer("4.0.0", "4.0.0-3-gabc123-dirty"))
        assertTrue(AppVersion.isNewer("4.0.1", "4.0.0-3-gabc123-dirty"))
        assertEquals("4.0.0", AppVersion.core("v4.0.0-3-gabc123"))
    }

    @Test
    fun missingSegmentsCountAsZero() {
        assertFalse(AppVersion.isNewer("4.0", "4.0.0"))
        assertTrue(AppVersion.isNewer("4.0.0.1", "4.0"))
    }
}
