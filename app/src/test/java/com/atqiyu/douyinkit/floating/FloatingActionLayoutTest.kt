package com.atqiyu.douyinkit.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingActionLayoutTest {

    @Test
    fun everyActiveCombinationKeepsButtonsApart() {
        val kinds = FloatingActionKind.entries
        for (mask in 0 until (1 shl kinds.size)) {
            val active = kinds.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
            val angles = FloatingActionLayout.angles(active)
            assertEquals(active.size, angles.size)
            if (angles.size >= 2) {
                val gap = FloatingActionLayout.minGapDegrees(angles.values)
                assertTrue(
                    "combination $active has gap $gap",
                    gap >= FloatingActionLayout.MIN_SEPARATION_DEGREES - 0.001,
                )
            }
        }
    }

    @Test
    fun stopPushesOpenButtonAside() {
        val withoutStop = FloatingActionLayout.angles(
            setOf(
                FloatingActionKind.PRIMARY,
                FloatingActionKind.INPUT,
                FloatingActionKind.OPEN_APP,
                FloatingActionKind.RESULT,
            ),
        )
        val withStop = FloatingActionLayout.angles(
            setOf(
                FloatingActionKind.PRIMARY,
                FloatingActionKind.INPUT,
                FloatingActionKind.OPEN_APP,
                FloatingActionKind.STOP,
                FloatingActionKind.RESULT,
            ),
        )
        assertEquals(0.0, withoutStop.getValue(FloatingActionKind.OPEN_APP), 0.001)
        assertEquals(-14.0, withStop.getValue(FloatingActionKind.OPEN_APP), 0.001)
        assertEquals(40.0, withStop.getValue(FloatingActionKind.STOP), 0.001)
        assertEquals(94.0, withStop.getValue(FloatingActionKind.RESULT), 0.001)
    }

    @Test
    fun panelCloseUsesUpperAreaAndMovesInputAside() {
        val angles = FloatingActionLayout.angles(
            setOf(
                FloatingActionKind.PRIMARY,
                FloatingActionKind.CLOSE_PANEL,
                FloatingActionKind.INPUT,
                FloatingActionKind.OPEN_APP,
            ),
        )
        assertEquals(-90.0, angles.getValue(FloatingActionKind.PRIMARY), 0.001)
        assertEquals(-144.0, angles.getValue(FloatingActionKind.CLOSE_PANEL), 0.001)
        assertEquals(162.0, angles.getValue(FloatingActionKind.INPUT), 0.001)
        assertEquals(54.0, FloatingActionLayout.minGapDegrees(angles.values), 0.001)
    }

    @Test
    fun rotationKeepsRelativeSeparation() {
        val angles = FloatingActionLayout.angles(FloatingActionKind.entries.toSet())
        val rotated = FloatingActionLayout.rotated(angles, 180)
        assertEquals(
            FloatingActionLayout.minGapDegrees(angles.values),
            FloatingActionLayout.minGapDegrees(rotated.values),
            0.001,
        )
        assertEquals(-90.0 + 180.0, rotated.getValue(FloatingActionKind.PRIMARY), 0.001)
    }
}