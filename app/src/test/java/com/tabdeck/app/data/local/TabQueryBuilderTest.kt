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
    fun acceptsPagingQueryAtPortableSqliteBindLimit() {
        val query = LibraryQuery(
            statuses = emptySet(),
            groups = (0 until 997).mapTo(linkedSetOf()) { "group-$it" },
        )

        TabQueryBuilder.requireSupported(query)
    }

    @Test
    fun rejectsPagingQueryAbovePortableSqliteBindLimit() {
        val query = LibraryQuery(
            statuses = emptySet(),
            groups = (0 until 998).mapTo(linkedSetOf()) { "group-$it" },
        )

        assertThrows(IllegalArgumentException::class.java) {
            TabQueryBuilder.requireSupported(query)
        }
    }

    @Test
    fun rejectsOversizedCombinedFilterCollectionsBeforeSqlGeneration() {
        val query = LibraryQuery(groups = (0 until 998).mapTo(linkedSetOf()) { "group-$it" })

        assertThrows(IllegalArgumentException::class.java) {
            TabQueryBuilder.requireSupported(query)
        }
    }

    @Test
    fun countQueryCanUseTheFullPortableBindBudget() {
        val query = LibraryQuery(
            statuses = emptySet(),
            groups = (0 until 999).mapTo(linkedSetOf()) { "group-$it" },
        )

        val built = TabQueryBuilder.count(query, staleBefore = 0L)

        assertEquals(999, built.argCount)
    }
}
