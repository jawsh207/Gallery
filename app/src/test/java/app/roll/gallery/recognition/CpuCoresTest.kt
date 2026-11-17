package app.roll.gallery.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class CpuCoresTest {
    @Test
    fun leavesOutThePowerSavingCores() {
        // Four small, three mid and one prime core.
        val speeds = List(4) { 1_950_000L } + List(3) { 2_600_000L } + 3_100_000L
        assertEquals(4, performanceCoreCount(speeds, 8))
    }

    @Test
    fun usesEveryCoreWhenAllAreBig() {
        val speeds = List(6) { 3_530_000L } + List(2) { 4_320_000L }
        assertEquals(8, performanceCoreCount(speeds, 8))
    }

    @Test
    fun fallsBackToHalfWhenSpeedsAreUnknown() {
        assertEquals(4, performanceCoreCount(List(8) { 0L }, 8))
        assertEquals(2, performanceCoreCount(emptyList(), 2))
    }
}
