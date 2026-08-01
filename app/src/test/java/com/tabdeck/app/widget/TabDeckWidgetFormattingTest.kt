package com.tabdeck.app.widget

import org.junit.Assert.assertTrue
import org.junit.Test

class TabDeckWidgetFormattingTest {
    @Test
    fun epochTimestampFormatsAsDate() {
        assertTrue(formatWidgetTime(0L).isNotBlank())
    }
}
