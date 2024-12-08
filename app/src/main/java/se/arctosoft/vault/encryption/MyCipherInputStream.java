/*
 * Valv-Android
 * Copyright (C) 2024 Arctosoft AB
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package se.arctosoft.vault.encryption;

import java.io.IOException;
import java.io.InputStream;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

public class MyCipherInputStream extends CipherInputStream {

    public MyCipherInputStream(InputStream is, Cipher c) {
        super(is, c);
        input = is;
        cipher = c;
    }

    private final Cipher cipher;
    private final InputStream input;
    private final byte[] ibuffer = new byte[1024 * 8];

    // having reached the end of the underlying input stream
    private boolean done = false;

    /* the buffer holding data that have been processed by the cipher
       engine, but have not been read out */
    private byte[] obuffer;
    // the offset pointing to the next "new" byte
    private int ostart = 0;
    // the offset pointing to the last "new" byte
    private int ofinish = 0;
    // stream status
    private boolean closed = false;

    private int getMoreData() throws IOException {
        if (done) {
            return -1;
        }
        ofinish = 0;
        ostart = 0;
        int expectedOutputSize = cipher.getOutputSize(ibuffer.length);
        if (obuffer == null || expectedOutputSize > obuffer.length) {
            obuffer = new byte[expectedOutputSize];
        }
        int readin = input.read(ibuffer);
        if (readin == -1) {
            done = true;
            try {
                // doFinal resets the cipher and it is the final call that is made. If there isn't
                // any more byte available, it returns 0. In case of any exception is raised,
                // obuffer will get reset and therefore, it is equivalent to no bytes returned.
                ofinish = cipher.doFinal(obuffer, 0);
            } catch (IllegalBlockSizeException | BadPaddingException e) {
                obuffer = null;
                throw new IOException(e);
            } catch (ShortBufferException e) {
                obuffer = null;
                throw new IllegalStateException("ShortBufferException is not expected", e);
            }
        } else {
            // update returns number of bytes stored in obuffer.
            try {
                ofinish = cipher.update(ibuffer, 0, readin, obuffer, 0);
            } catch (IllegalStateException e) {
                obuffer = null;
                throw e;
            } catch (ShortBufferException e) {
                // Should not reset the value of ofinish as the cipher is still not invalidated.
                obuffer = null;
                throw new IllegalStateException("ShortBufferException is not expected", e);
            }
        }
        return ofinish;
    }

    @Override
    public int read() throws IOException {
        if (ostart >= ofinish) {
            // we loop for new data as the spec says we are blocking
            int i = 0;
            while (i == 0) i = getMoreData();
            if (i == -1) return -1;
        }
        return ((int) obuffer[ostart++] & 0xff);
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (ostart >= ofinish) {
            // we loop for new data as the spec says we are blocking
            int i = 0;
            while (i == 0) i = getMoreData();
            if (i == -1) return -1;
        }
        if (len <= 0) {
            return 0;
        }
        int available = ofinish - ostart;
        if (len < available) available = len;
        if (b != null) {
            System.arraycopy(obuffer, ostart, b, off, available);
        }
        ostart = ostart + available;
        return available;
    }

    @Override
    public long skip(long n) {
        int available = ofinish - ostart;
        if (n > available) {
            n = available;
        }
        if (n < 0) {
            return 0;
        }
        ostart += (int) n;
        return n;
    }

    @Override
    public int available() {
        return (ofinish - ostart);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;
        input.close();

        if (!done) {
            try {
                cipher.doFinal();
            } catch (BadPaddingException | IllegalBlockSizeException ex) {
                if (ex instanceof AEADBadTagException) {
                    throw new IOException(ex);
                }
            }
        }
        ostart = 0;
        ofinish = 0;
    }

    @Override
    public boolean markSupported() {
        return false;
    }
}
