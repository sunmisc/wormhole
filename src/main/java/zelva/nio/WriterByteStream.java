package zelva.nio;

import java.util.Arrays;

import static zelva.nio.ByteArrayView.*;

public class WriterByteStream {
    static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;
    private byte[] buf;
    private int pos;

    public WriterByteStream() {
        this.buf = new byte[32];
    }

    public WriterByteStream(int size) {
        this.buf = new byte[size];
    }
    public void put(byte b) {
        int p = pos;
        ensureCapacity(pos = p + 1)[p] = b;
    }
    public WriterByteStream put(byte[] b) {
        return put(b, 0);
    }
    public WriterByteStream put(byte[] b, int off) {
        int p = pos, len = b.length;
        System.arraycopy(b, off, ensureCapacity(pos = p + len), p, len);
        return this;
    }
    public WriterByteStream putInt(int value) {
        int c = pos;
        IVH.set(ensureCapacity(pos = c + 4), c, value);
        return this;
    }

    public WriterByteStream putLong(long value) {
        int c = pos;
        LVH.set(buf, pos = c + 8, value);
        return this;
    }
    public WriterByteStream putShort(short value) {
        int c = pos;
        SVH.set(ensureCapacity(pos = c + 2), c, value);
        return this;
    }
    public WriterByteStream putChar(char value) {
        int c = pos;
        CVH.set(ensureCapacity(pos = c + 2), c, value);
        return this;
    }
    public WriterByteStream putDouble(double value) {
        int c = pos;
        DVH.set(ensureCapacity(pos = c + 8), c, value);
        return this;
    }
    public WriterByteStream putFloat(float value) {
        int c = pos;
        FVH.set(ensureCapacity(pos = c + 4), c, value);
        return this;
    }

    private byte[] ensureCapacity(int minCapacity) {
        byte[] arr = buf;
        int oldCapacity = arr.length;
        int minGrowth = minCapacity - oldCapacity;
        return minGrowth > 0
                ? buf = Arrays.copyOf(arr, newLength(oldCapacity, minGrowth, oldCapacity))
                : arr;
    }
    private static int newLength(int oldLength, int minGrowth, int prefGrowth) {
        int newLength = Math.max(minGrowth, prefGrowth) + oldLength;
        if (newLength - MAX_ARRAY_LENGTH <= 0) {
            return newLength;
        }
        int minLength = oldLength + minGrowth;
        if (minLength < 0) { // overflow
            throw new OutOfMemoryError("Required array length too large");
        }
        if (minLength <= MAX_ARRAY_LENGTH) {
            return MAX_ARRAY_LENGTH;
        }
        return Integer.MAX_VALUE;
    }
    public byte[] toByteArray() {
        return Arrays.copyOf(buf, pos);
    }
    public ReaderByteStream toReaderByteStream() {
        return new ReaderByteStream(buf, pos);
    }
    byte[] getBufferArray() {
        return buf;
    }
}
