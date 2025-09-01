# Book Barcode Scanner
## Concept
The idea behind this project is to allow for scanning of barcodes on books to retrieve the ISBN and upload it to a home catalog that can be shared with others.
## Process
- Use ML Kit to scan barcodes
- Verify the barcode is valid by using the ISBN 13 checksum
- Determine if valid ISBNs are found using the Google Books API
- Display the book title with a thubmnail
- Allow users to remove books once added to the list by swiping
