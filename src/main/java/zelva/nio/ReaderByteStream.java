package zelva.nio;

import java.io.InputStream;
import java.nio.BufferUnderflowException;

import static zelva.nio.ByteArrayView.*;

public class ReaderByteStream extends InputStream {
    private final byte[] buf;
    private final int limit;
    private int pos;

    public ReaderByteStream(byte[] buf, int limit) {
        this.buf = buf;
        this.limit = limit;
    }

    public ReaderByteStream(byte[] buf) {
        this.buf = buf;
        this.limit = buf.length;
    }

    @Override
    public int read() {
        return buf[nextPutIndex(1)] & 0xff;
    }

    public int getInt() {
        return (int) IVH.get(buf, nextPutIndex(4));
    }

    public long getLong() {
        return (long) LVH.get(buf, nextPutIndex(8));
    }

    public char getChar() {
        return (char) CVH.get(buf, nextPutIndex(2));
    }

    public short getShort() {
        return (short) SVH.get(buf, nextPutIndex(2));
    }

    public float getFloat() {
        return (float) FVH.get(buf, nextPutIndex(4));
    }

    public double getDouble() {
        return (double) DVH.get(buf, nextPutIndex(8));
    }

    public byte[] getArray(int len) {
        int p = nextPutIndex(len);
        byte[] copy = new byte[len];
        System.arraycopy(buf, p, copy, 0, len);
        return copy;
    }

    private int nextPutIndex(int len) {
        int p = pos;
        if (p >= limit)
            throw new IndexOutOfBoundsException();
        pos = p + len;
        return p;
    }

    public byte peek() {
        int p = pos;
        if (p < limit)
            return buf[p];
        throw new BufferUnderflowException();
    }

    public void reset() {
        pos = 0;
    }

    public void back(int i) {
        int p = pos - i;
        if (p < 0)
            throw new IndexOutOfBoundsException();
        pos = p;
    }

    public void position(int i) {
        if (i >= 0 && i <= limit)
            pos = i;
        throw new IndexOutOfBoundsException();
    }

    public void skip(int i) {
        int p = pos + i;
        if (p < 0 || p > limit)
            throw new IndexOutOfBoundsException();
        pos = p;
    }
}