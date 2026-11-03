package com.atqiyu.douyinkit.floating

/**
 * Pure geometry for the floating action ring. The preferred angles keep frequently used
 * actions at familiar positions; when "stop" or the panel close button appears, neighbouring
 * actions shift just enough to keep a fixed minimum gap between 42dp buttons.
 */
internal enum class FloatingActionKind { PRIMARY, CLOSE_PANEL, INPUT, OPEN_APP, STOP, RESULT }

internal object FloatingActionLayout {
    /** Minimum center-to-center angle that keeps 42dp buttons from visually touching. */
    const val MIN_SEPARATION_DEGREES = 54.0

    private const val PRIMARY_ANGLE = -90.0
    private const val CLOSE_ANGLE = -144.0
    private const val INPUT_ANGLE = 180.0
    private const val INPUT_ANGLE_WITH_CLOSE = 162.0
    private const val OPEN_ANGLE = 0.0
    private const val OPEN_ANGLE_WITH_STOP = -14.0
    private const val STOP_ANGLE = 40.0
    private const val RESULT_ANGLE = 90.0
    private const val RESULT_ANGLE_WITH_STOP = 94.0

    fun angles(active: Set<FloatingActionKind>): Map<FloatingActionKind, Double> {
        if (active.isEmpty()) return emptyMap()
        val hasStop = FloatingActionKind.STOP in active
        val hasClose = FloatingActionKind.CLOSE_PANEL in active
        return buildMap {
            if (FloatingActionKind.PRIMARY in active) {
                put(FloatingActionKind.PRIMARY, PRIMARY_ANGLE)
            }
            if (hasClose) {
                put(FloatingActionKind.CLOSE_PANEL, CLOSE_ANGLE)
            }
            if (FloatingActionKind.INPUT in active) {
                put(
                    FloatingActionKind.INPUT,
                    if (hasClose) INPUT_ANGLE_WITH_CLOSE else INPUT_ANGLE,
                )
            }
            if (FloatingActionKind.OPEN_APP in active) {
                put(
                    FloatingActionKind.OPEN_APP,
                    if (hasStop) OPEN_ANGLE_WITH_STOP else OPEN_ANGLE,
                )
            }
            if (hasStop) {
                put(FloatingActionKind.STOP, STOP_ANGLE)
            }
            if (FloatingActionKind.RESULT in active) {
                put(
                    FloatingActionKind.RESULT,
                    if (hasStop) RESULT_ANGLE_WITH_STOP else RESULT_ANGLE,
                )
            }
        }
    }

    fun rotated(
        angles: Map<FloatingActionKind, Double>,
        offsetDegrees: Int,
    ): Map<FloatingActionKind, Double> {
        if (offsetDegrees == 0) return angles
        return angles.mapValues { (_, angle) -> angle + offsetDegrees }
    }

    fun minGapDegrees(angles: Collection<Double>): Double {
        if (angles.size < 2) return 360.0
        val sorted = angles
            .map { ((it % 360.0) + 360.0) % 360.0 }
            .sorted()
        var min = 360.0
        for (index in sorted.indices) {
            val next = if (index == sorted.lastIndex) sorted.first() + 360.0 else sorted[index + 1]
            min = minOf(min, next - sorted[index])
        }
        return min
    }
}