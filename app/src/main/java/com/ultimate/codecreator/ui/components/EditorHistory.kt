package com.ultimate.codecreator.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue

class EditorHistory(private val maxEntries: Int = 200) {
    private val entries = mutableListOf<TextFieldValue>()
    private var index = -1
    private var lastRecordTime = 0L
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    fun record(value: TextFieldValue) {
        val now = System.currentTimeMillis()
        if (index >= 0 && entries[index].text == value.text) {
            entries[index] = value
            return
        }
        if (index < entries.lastIndex) entries.subList(index + 1, entries.size).clear()
        if (now - lastRecordTime < 400 && index >= 0) {
            entries[index] = value
        } else {
            entries.add(value)
            if (entries.size > maxEntries) entries.removeAt(0)
            index = entries.lastIndex
        }
        lastRecordTime = now
        updateFlags()
    }

    fun undo(): TextFieldValue? {
        if (index <= 0) return null
        index--
        updateFlags()
        return entries[index]
    }

    fun redo(): TextFieldValue? {
        if (index >= entries.lastIndex) return null
        index++
        updateFlags()
        return entries[index]
    }

    private fun updateFlags() {
        canUndo = index > 0
        canRedo = index < entries.lastIndex
    }
}
