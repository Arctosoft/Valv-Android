/*
 * Valv-Android
 * Copyright (C) 2023 Arctosoft AB
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

    public MyCipherInputStream(InputStream is, Cipher c) throws IOException {
        super(is, c);
        input = is;
        cipher = c;
    }


    // the cipher engine to use to process stream data
    private Cipher cipher;

    // the underlying input stream
    private InputStream input;

    /* the buffer holding data that have been read in from the
       underlying stream, but have not been processed by the cipher
       engine. the size 512 bytes is somewhat randomly chosen */
    private byte[] ibuffer = new byte[1024*8]; // Increased buffer size

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

    /**
     * private convenience function.
     *
     * Entry condition: ostart = ofinish
     *
     * Exit condition: ostart <= ofinish
     *
     * return (ofinish-ostart) (we have this many bytes for you)
     * return 0 (no data now, but could have more later)
     * return -1 (absolutely no more data)
     *
     * Note:  Exceptions are only thrown after the stream is completely read.
     * For AEAD ciphers a read() of any length will internally cause the
     * whole stream to be read fully and verify the authentication tag before
     * returning decrypted data or exceptions.
     */
    private int getMoreData() throws IOException {
        // Android-changed: The method was creating a new object every time update(byte[], int, int)
        // or doFinal() was called resulting in the old object being GCed. With do(byte[], int) and
        // update(byte[], int, int, byte[], int), we use already initialized obuffer.
        if (done) return -1;
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

    /**
     * Reads the next byte of data from this input stream. The value
     * byte is returned as an <code>int</code> in the range
     * <code>0</code> to <code>255</code>. If no byte is available
     * because the end of the stream has been reached, the value
     * <code>-1</code> is returned. This method blocks until input data
     * is available, the end of the stream is detected, or an exception
     * is thrown.
     * <p>
     *
     * @return  the next byte of data, or <code>-1</code> if the end of the
     *          stream is reached.
     * @exception  IOException  if an I/O error occurs.
     * @since JCE1.2
     */
    public int read() throws IOException {
        if (ostart >= ofinish) {
            // we loop for new data as the spec says we are blocking
            int i = 0;
            while (i == 0) i = getMoreData();
            if (i == -1) return -1;
        }
        return ((int) obuffer[ostart++] & 0xff);
    };

    /**
     * Reads up to <code>b.length</code> bytes of data from this input
     * stream into an array of bytes.
     * <p>
     * The <code>read</code> method of <code>InputStream</code> calls
     * the <code>read</code> method of three arguments with the arguments
     * <code>b</code>, <code>0</code>, and <code>b.length</code>.
     *
     * @param      b   the buffer into which the data is read.
     * @return     the total number of bytes read into the buffer, or
     *             <code>-1</code> is there is no more data because the end of
     *             the stream has been reached.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.InputStream#read(byte[], int, int)
     * @since      JCE1.2
     */
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Reads up to <code>len</code> bytes of data from this input stream
     * into an array of bytes. This method blocks until some input is
     * available. If the first argument is <code>null,</code> up to
     * <code>len</code> bytes are read and discarded.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in the destination array
     *                   <code>buf</code>
     * @param      len   the maximum number of bytes read.
     * @return     the total number of bytes read into the buffer, or
     *             <code>-1</code> if there is no more data because the end of
     *             the stream has been reached.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.InputStream#read()
     * @since      JCE1.2
     */
    public int read(byte b[], int off, int len) throws IOException {
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

    /**
     * Skips <code>n</code> bytes of input from the bytes that can be read
     * from this input stream without blocking.
     *
     * <p>Fewer bytes than requested might be skipped.
     * The actual number of bytes skipped is equal to <code>n</code> or
     * the result of a call to
     * {@link #available() available},
     * whichever is smaller.
     * If <code>n</code> is less than zero, no bytes are skipped.
     *
     * <p>The actual number of bytes skipped is returned.
     *
     * @param      n the number of bytes to be skipped.
     * @return     the actual number of bytes skipped.
     * @exception  IOException  if an I/O error occurs.
     * @since JCE1.2
     */
    public long skip(long n) throws IOException {
        int available = ofinish - ostart;
        if (n > available) {
            n = available;
        }
        if (n < 0) {
            return 0;
        }
        ostart += n;
        return n;
    }

    /**
     * Returns the number of bytes that can be read from this input
     * stream without blocking. The <code>available</code> method of
     * <code>InputStream</code> returns <code>0</code>. This method
     * <B>should</B> be overridden by subclasses.
     *
     * @return     the number of bytes that can be read from this input stream
     *             without blocking.
     * @exception  IOException  if an I/O error occurs.
     * @since      JCE1.2
     */
    public int available() throws IOException {
        return (ofinish - ostart);
    }

    /**
     * Closes this input stream and releases any system resources
     * associated with the stream.
     * <p>
     * The <code>close</code> method of <code>CipherInputStream</code>
     * calls the <code>close</code> method of its underlying input
     * stream.
     *
     * @exception  IOException  if an I/O error occurs.
     * @since JCE1.2
     */
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;
        input.close();

        // Android-removed: Removed a now-inaccurate comment
        if (!done) {
            try {
                cipher.doFinal();
            }
            catch (BadPaddingException | IllegalBlockSizeException ex) {
                // Android-changed: Added throw if bad tag is seen.  See b/31590622.
                if (ex instanceof AEADBadTagException) {
                    throw new IOException(ex);
                }
            }
        }
        ostart = 0;
        ofinish = 0;
    }

    /**
     * Tests if this input stream supports the <code>mark</code>
     * and <code>reset</code> methods, which it does not.
     *
     * @return  <code>false</code>, since this class does not support the
     *          <code>mark</code> and <code>reset</code> methods.
     * @see     java.io.InputStream#mark(int)
     * @see     java.io.InputStream#reset()
     * @since   JCE1.2
     */
    public boolean markSupported() {
        return false;
    }
}
