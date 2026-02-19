# Book Barcode Scanner
## Setup
Copy `local.properties.example` to `local.properties` and add your Google OAuth Client ID and backend URL. Never commit `local.properties`—it contains credentials.

## Concept
The idea behind this project is to allow for scanning of barcodes on books to retrieve the ISBN and upload it to a home catalog that can be shared with others.
## Process
- Use ML Kit to scan barcodes
- Verify the barcode is valid by using the ISBN 13 checksum
- Determine if valid ISBNs are found using the Google Books API
- Display the book title with a thubmnail in a list
- Allow users to remove books from teh list by swiping
