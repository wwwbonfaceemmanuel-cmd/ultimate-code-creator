package com.ultimate.codecreator.engine

enum class DiffType { CONTEXT, ADDED, REMOVED }

data class DiffLine(
    val type: DiffType,
    val text: String,
    val oldLine: Int?,
    val newLine: Int?
)

/** Small line diff for repair review. Falls back to whole-block diff for very large inputs. */
object DiffEngine {
    private const val MAX_LCS_LINES = 1_200

    fun diff(original: String, repaired: String): List<DiffLine> {
        if (original == repaired) return original.lines().mapIndexed { index, line -> DiffLine(DiffType.CONTEXT, line, index + 1, index + 1) }
        val oldLines = original.lines()
        val newLines = repaired.lines()
        if (oldLines.size > MAX_LCS_LINES || newLines.size > MAX_LCS_LINES) {
            return oldLines.mapIndexed { index, line -> DiffLine(DiffType.REMOVED, line, index + 1, null) } +
                newLines.mapIndexed { index, line -> DiffLine(DiffType.ADDED, line, null, index + 1) }
        }
        val rows = oldLines.size
        val cols = newLines.size
        val dp = Array(rows + 1) { IntArray(cols + 1) }
        for (i in rows - 1 downTo 0) {
            for (j in cols - 1 downTo 0) {
                dp[i][j] = if (oldLines[i] == newLines[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val result = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < rows && j < cols) {
            when {
                oldLines[i] == newLines[j] -> {
                    result.add(DiffLine(DiffType.CONTEXT, oldLines[i], i + 1, j + 1)); i++; j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    result.add(DiffLine(DiffType.REMOVED, oldLines[i], i + 1, null)); i++
                }
                else -> {
                    result.add(DiffLine(DiffType.ADDED, newLines[j], null, j + 1)); j++
                }
            }
        }
        while (i < rows) { result.add(DiffLine(DiffType.REMOVED, oldLines[i], i + 1, null)); i++ }
        while (j < cols) { result.add(DiffLine(DiffType.ADDED, newLines[j], null, j + 1)); j++ }
        return result
    }
}
