package com.lightdev.khovault

import org.junit.Assert.assertEquals
import org.junit.Test

class DiamondMathTest {
    private val entries = listOf(
        DiamondEntry(1, 500, "Nạp", "", 1),
        DiamondEntry(2, -120, "Chi", "", 2),
        DiamondEntry(3, 70, "Thẻ", "", 3)
    )

    @Test fun calculatesBalance() = assertEquals(450, DiamondMath.balance(entries))
    @Test fun separatesReceivedAndSpent() {
        assertEquals(570, DiamondMath.received(entries))
        assertEquals(120, DiamondMath.spent(entries))
    }
    @Test fun clampsGoalProgress() = assertEquals(1f, DiamondMath.goalProgress(600, 500))
}
