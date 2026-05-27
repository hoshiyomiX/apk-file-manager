package com.hoshiyomi.filemanager.model

enum class SortMode {
    NAME, SIZE, DATE, TYPE;

    fun next(): SortMode = when (this) {
        NAME -> SIZE
        SIZE -> DATE
        DATE -> TYPE
        TYPE -> NAME
    }
}

enum class SortOrder {
    ASCENDING, DESCENDING;

    fun toggle(): SortOrder = if (this == ASCENDING) DESCENDING else ASCENDING
}
