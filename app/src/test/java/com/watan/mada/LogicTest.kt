package com.watan.mada
import org.junit.Assert.*
import org.junit.Test
class LogicTest {
    @Test fun ops() { assertEquals("زين Zain", operatorName("419", "02", null)); assertEquals("stc الكويت", operatorName("419", "04", "VIVA")) }
    @Test fun bands() { assertEquals("B3 1800", lteBand(1850)); assertEquals("n78 3500", nrBand(636666)) }
    @Test fun compassT() { assertEquals("الشمال", compass(5.0)); assertEquals("الشرق", compass(90.0)) }
}
