package com.lightdev.khovault

object DiamondMath {
    fun balance(entries: List<DiamondEntry>): Int = entries.sumOf { it.amount }
    fun received(entries: List<DiamondEntry>): Int = entries.filter { it.amount > 0 }.sumOf { it.amount }
    fun spent(entries: List<DiamondEntry>): Int = -entries.filter { it.amount < 0 }.sumOf { it.amount }
    fun goalProgress(balance: Int, goal: Int): Float =
        if (goal <= 0) 0f else (balance.toFloat() / goal).coerceIn(0f, 1f)
}
