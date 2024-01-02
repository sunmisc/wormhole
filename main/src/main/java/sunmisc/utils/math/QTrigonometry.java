package sunmisc.utils.math;

public class QTrigonometry implements Trigonometry {
    private static final int PRECISION      = 1 << 16;
    private static final int[] SINE_TABLE_INT;

    static {
        int n = (PRECISION >>> 2) + 1;
        SINE_TABLE_INT = new int[n];
        double delta = Math.TAU / PRECISION;
        for (int i = 0; i < n; i++) {
            float f = (float) Math.sin(i * delta);
            SINE_TABLE_INT[i] = Float.floatToRawIntBits(f);
        }
    }

    @Override
    public FSin sin(Number x) {
        return new FSin(x);
    }

    @Override
    public FCos cos(Number x) {
        return new FCos(x);
    }

    public final static class FSin extends Number {

        private final Number number;

        public FSin(Number number) {
            this.number = number;
        }

        private float compute() {
            double h = PRECISION / Math.TAU;
            return lookup((int)(number.floatValue() * h));
        }

        @Override public int intValue() { return (int) compute(); }

        @Override public long longValue() { return intValue(); }

        @Override public float floatValue() { return compute(); }

        @Override
        public double doubleValue() {
            return Math.sin(number.doubleValue());
        }
    }
    public final static class FCos extends Number {

        private final Number number;

        public FCos(Number number) {
            this.number = number;
        }

        private float compute() {
            double h = PRECISION / Math.TAU, q = PRECISION >>> 2;
            return lookup((int)(number.floatValue() * h + q));
        }

        @Override public int intValue() { return (int) compute(); }

        @Override public long longValue() { return intValue(); }

        @Override public float floatValue() { return compute(); }

        @Override
        public double doubleValue() {
            return Math.cos(number.doubleValue());
        }
    }
    /*
     * Author of the original implementation in Rust:
     * https://gitlab.com/coderbot16/i73/-/tree/master/i73-trig/src
     */
    private static float lookup(int index) {
        index &= (PRECISION - 1);

        int h = PRECISION >> 1;
        // Trigonometric identity: sin(-x) = -sin(x)
        // Given a domain of 0 <= x <= 2*pi, just negate the value if x > pi.
        // This allows the sin table size to be halved.

        int neg = (index & h) << 16;

        // All bits set if (pi/2 <= x), none set otherwise
        // Extracts the 15th bit from 'half'
        int mask = (index << 17) >> 31;

        // Trigonometric identity: sin(x) = sin(pi/2 - x)
        int pos = ((h + 1) & mask) + (index ^ mask);

        // Wrap the position in the table. Moving this down to immediately before the array access
        // seems to help the Hotspot compiler optimize the bit math better.
        pos &= (h - 1);

        // Fetch the corresponding value from the LUT and invert the sign bit as needed
        // This directly manipulate the sign bit on the float bits to simplify logic
        return Float.intBitsToFloat(SINE_TABLE_INT[pos] ^ neg);
    }

}
