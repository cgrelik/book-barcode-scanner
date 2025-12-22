package xyz.cgsoftware.barcodescanner.models

data class Book(val id: String = "",
           val isbn13: String = "",
           val isbn10: String = "",
           val title: String = "",
           val thumbnail: String? = "",
           val tags: List<Tag> = emptyList())