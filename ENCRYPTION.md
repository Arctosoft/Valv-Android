
# File encryption
Files are encrypted using the `ChaCha20/NONE/NoPadding` cipher in Android. See [Android Ciphers](https://developer.android.com/reference/javax/crypto/Cipher) for details.

The key algorithm is `PBKDF2withHmacSHA512` with 20000 iterations and a 256-bit key length.

The salt is 16 bytes and the IV is 12 bytes. An additional 12 bytes is used in some files to check if the supplied password can decrypt the file, see details below.

## Encrypted file structure
![Encrypted file structure image](/images/encryption.jpg)

## File types/names

The following filename prefixes are used:
- `.valv.i.1-` for image files
- `.valv.g.1-` for GIF files
- `.valv.v.1-` for video files
- `.valv.n.1-` for note files
- `.valv.t.1-` for thumbnail files

The number represents the file structure version (for future expansion/changes).

Filenames are generated randomly and are `PREFIX_LENGTH + 32 chars` long.
Every media file has a corresponding thumbnail with the same name. For example, an image file named

`.valv.i.1-aLFshh71iywWo7HXtEcOtZNVJe-Ot7iQ` has a thumbnail

`.valv.t.1-aLFshh71iywWo7HXtEcOtZNVJe-Ot7iQ`.

Similarly, a note has the same name as its media file.

## Encrypting
The app creates the encrypted files in the following way:
1. Generate a random 16 byte salt and 12 byte IV. If the file is a thumbnail it also generates an additional 12 check bytes.
2. Create an unencrypted output stream.
3. Write the salt.
4. Write the IV.
5. If the file is a thumbnail, write the check bytes.
6. Pass the output stream into a cipher (encrypted) output stream. Everything below is encrypted.
7. If the file is a thumbnail, write the check bytes.
8. Write a newline character followed by the original filename and another newline character (`'\n' + name + '\n'`).
9. Write the file data.

## Decrypting
The app reads the encrypted files in the following way:
1. Create an unencrypted input stream.
2. Read the 16 byte salt.
3. Read the 12 byte IV.
4. If the file is a thumbnail, read the 12 check bytes.
5. Pass the input stream into a cipher (encrypted) input stream. Everything below is read from encrypted data.
6. If the file is a thumbnail, read the check bytes. If the unencrypted check bytes does not equal the check bytes in the encrypted part, the given password is invalid.
7. Read a newline character (`0x0A`) followed by the original filename and another newline character.
8. Read the file data.

A Python script to decrypt .valv files can be found in [this issue comment](https://github.com/Arctosoft/Valv-Android/issues/33#issuecomment-1974834924).
