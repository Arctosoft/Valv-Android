This is the encryption docs for file structure version 2.

# File encryption
Files are encrypted using the `ChaCha20/NONE/NoPadding` cipher in Android. See [Android Ciphers](https://developer.android.com/reference/javax/crypto/Cipher) for details.

The key algorithm is `PBKDF2withHmacSHA512` with 50000 iterations (default, can be changed) and a 256-bit key length.

The salt is 16 bytes and the IV is 12 bytes. An additional 12 bytes is used to check if the supplied password can decrypt the file, see details below.

## Encrypted file structure
![Encrypted file structure image](/images/encryption_v2.jpg)

## File types/names

The following filename suffixes are used:
- `-i.valv` for image files
- `-g.valv` for GIF files
- `-v.valv` for video files
- `-x.valv` for text files
- `-n.valv` for note files
- `-t.valv` for thumbnail files

The number represents the file structure version (for future expansion/changes).

Filenames are generated randomly and are `32 chars + SUFFIX_LENGTH` long.
Every media file has a corresponding thumbnail with the same name. For example, an image file named

`aLFshh71iywWo7HXtEcOtZNVJe-Ot7iQ-i.valv` has a thumbnail

`aLFshh71iywWo7HXtEcOtZNVJe-Ot7iQ-t.valv`.

Similarly, a note has the same name as its media file.

All text and strings are encoded as UTF-8.

## Encrypting
The app creates the encrypted files in the following way:
1. Generate a random 16 byte salt, a 12 byte IV and 12 check bytes.
2. Create an unencrypted output stream.
3. Write the encrypted file structure version (4 bytes, integer)
4. Write the salt.
5. Write the IV.
6. Write the iteration count used for key generation (4 bytes, integer)
7. Write the check bytes.
8. Pass the output stream into a cipher (encrypted) output stream. Everything below is encrypted.
9. Write the check bytes.
10. Write a newline character followed by a JSON object as an string containing the original filename and another newline character (`'\n' + "{\"originalName\":\"file.jpg\"}" + '\n'`).
11. Write the file data.

## Decrypting
The app reads the encrypted files in the following way:
1. Create an unencrypted input stream.
2. Read the encrypted file structure version (4 bytes, integer)
3. Read the 16 byte salt.
4. Read the 12 byte IV.
5. Read the iteration count used for key generation (4 bytes, integer)
6. Read the 12 check bytes.
7. Pass the input stream into a cipher (encrypted) input stream. Everything below is read from encrypted data.
8. Read the check bytes. If the unencrypted check bytes does not equal the check bytes in the encrypted part, the given password is invalid.
9. Read a newline character (`0x0A`) followed by the JSON object string and another newline character.
10. Read the file data.

