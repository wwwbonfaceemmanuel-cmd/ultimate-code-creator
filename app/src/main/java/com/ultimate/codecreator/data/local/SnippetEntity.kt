package com.ultimate.codecreator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val language: String,
    val code: String,
    val tags: String,
    val favorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
