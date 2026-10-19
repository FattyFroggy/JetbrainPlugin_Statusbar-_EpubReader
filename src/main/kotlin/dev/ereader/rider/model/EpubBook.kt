package dev.ereader.rider.model

data class EpubBook(
    val title: String,
    val chapters: List<EpubChapter>,
)

data class EpubChapter(
    val title: String,
    val text: String,
)
