package com.tabdeck.app.data.local

import com.tabdeck.app.model.LibraryQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TabQueryBuilderTest {
    @Test
    fun rejectsQueriesThatExceedThePortableSqliteBindLimit() {
        val search = (0 until 143).joinToString(" ") { "token$it" }

        val error = assertThrows(IllegalArgumentException::class.java) {
            TabQueryBuilder.requireSupported(LibraryQuery(search = search))
        }

        assertTrue(error.message.orEmpty().contains("SQLite bind arguments"))
    }

    @Test
    fun repeatedSearchTermsDoNotConsumeAdditionalBindArguments() {
        val query = LibraryQuery(search = List(500) { "repeat" }.joinToString(" "))

        val built = TabQueryBuilder.count(query, staleBefore = 0L)

        assertEquals(8, built.argCount)
    }

    @Test
    fun rejectsOversizedCombinedFilterCollectionsBeforeSqlGeneration() {
        val query = LibraryQuery(groups = (0 until 998).mapTo(linkedSetOf()) { "group-$it" })

        assertThrows(IllegalArgumentException::class.java) {
            TabQueryBuilder.requireSupported(query)
        }
    }
}
