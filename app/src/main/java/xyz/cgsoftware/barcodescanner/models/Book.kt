package xyz.cgsoftware.barcodescanner.models

class Book(val id: String = "",
           val isbn13: String = "",
           val isbn10: String = "",
           val title: String = "",
           val thumbnail: String? = "") {
    override fun equals(other: Any?): Boolean {
        if (other is Book) {
            return this.isbn13 == other.isbn13
        }
        return false
    }
    override fun hashCode(): Int {
        return this.isbn13.hashCode()
    }
}