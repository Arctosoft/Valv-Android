# File encryption
Files are encrypted using the `ChaCha20/NONE/NoPadding` cipher in Android. See [Android Ciphers](https://developer.android.com/reference/javax/crypto/Cipher) for details.

The key algorithm is `PBKDF2withHmacSHA512` with 20000 iterations and a 256-bit key length.

The salt is 16 bytes and the IV is 12 bytes. A checksum of 12 bytes is also used to check if the supplied password can decrypt the file, see details below.

## Encrypting

## Decrypting

## File names