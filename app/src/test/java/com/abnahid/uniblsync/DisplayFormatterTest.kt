package com.abnahid.uniblsync

import com.abnahid.uniblsync.util.DisplayFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayFormatterTest {
    @Test fun `formats API enum labels without changing values`() {
        assertEquals("In Person", DisplayFormatter.label("IN_PERSON"))
        assertEquals("Group 4", DisplayFormatter.group("GROUP_4"))
        assertEquals("Make-up Lecture", DisplayFormatter.label("MAKE_UP_LECTURE"))
        assertEquals("Unknown Value", DisplayFormatter.label("UNKNOWN_VALUE"))
    }
}
