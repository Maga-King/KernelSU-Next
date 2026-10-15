// Motion timing from lyravoid/Aster, GPL-3.0.
package com.rifsxd.ksunext.ui.shell

import kotlin.math.abs

/** Aster gives distant primary pages longer to travel, with a 300 ms minimum. */
internal fun primaryPageDuration(initialIndex: Int, targetIndex: Int): Int =
    100 * abs(targetIndex - initialIndex).coerceAtLeast(2) + 100
