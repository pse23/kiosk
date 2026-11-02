package com.shapeshed.kiosk.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(matchesLibraryQuery("", "anything"))
        assertTrue(matchesLibraryQuery("   ", null))
    }

    @Test
    fun matchingIgnoresCase() {
        assertTrue(matchesLibraryQuery("postgres", "Postgres 19 is late"))
    }

    @Test
    fun everyTermMustMatchSomeField() {
        assertTrue(matchesLibraryQuery("postgres blogspot", "Postgres 19 is late", "rhaas.blogspot.com"))
        assertFalse(matchesLibraryQuery("postgres mysql", "Postgres 19 is late", "rhaas.blogspot.com"))
    }

    @Test
    fun nullFieldsNeverMatch() {
        assertFalse(matchesLibraryQuery("x", null, null))
    }
}
