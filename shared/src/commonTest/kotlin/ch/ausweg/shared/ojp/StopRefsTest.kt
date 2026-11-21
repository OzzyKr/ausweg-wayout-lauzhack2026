package ch.ausweg.shared.ojp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StopRefsTest {
    @Test
    fun didokNumberIsKept() {
        assertEquals(8501120, didokOf("8501120"))
    }

    @Test
    fun stationSloidMapsToDidok() {
        assertEquals(8507000, didokOf("ch:1:sloid:7000"))
        assertEquals(8501120, didokOf("ch:1:sloid:1120"))
    }

    @Test
    fun platformSloidMapsToItsStation() {
        assertEquals(8507000, didokOf("ch:1:sloid:7000:4:7"))
        assertEquals(8590034, didokOf("ch:1:sloid:90034:0:2"))
    }

    @Test
    fun nonSwissOrBrokenRefsGiveNull() {
        assertNull(didokOf("de:00000:-1"))
        assertNull(didokOf("ch:1:sloid:"))
        assertNull(didokOf("1234567"))
        assertNull(didokOf(""))
    }
}
