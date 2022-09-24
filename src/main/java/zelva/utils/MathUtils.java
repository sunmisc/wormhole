package zelva.utils;


/**
 * @author ZelvaLea
 */
public final class MathUtils {
    public static final double _2PI         = 2 * Math.PI;

    private static final int MAX_PRECISION = (1 << 16);
    private static final int _N             = MAX_PRECISION - 1;
    private static final int _COS           = MAX_PRECISION >> 2;
    private static final double _H          = MAX_PRECISION /_2PI;

    private static final int[] SINE_TABLE_INT = new int[(1 << 14) + 1];

    static {
        /**
         * @author coderbot16   Author of the original (and very clever) implementation in Rust:
         *  <a href="https://gitlab.com/coderbot16/i73/-/tree/master/i73-trig/src">...</a>
         */
        final double delta = _2PI / MAX_PRECISION;
        for (int i = 0, len = SINE_TABLE_INT.length; i < len; i++) {
            float f = (float) Math.sin(i * delta);
            SINE_TABLE_INT[i] = Float.floatToRawIntBits(f);
        }
    }

    private MathUtils() {}

    public static float _sin(double value) {
        return lookup(_N & ((int)(value*_H)));
    }
    public static float _cos(double value) {
        return lookup(_N & ((int)(value*_H+_COS)));
    }

    public static int sumArithmeticProgression(int n) {
        int y = n + 1;
        return (y & 1) == 0
                ? n * (y >> 1)
                : (n >> 1) * y;
    }


    private static float lookup(int index) {
        int half = MAX_PRECISION >> 1;

        // Trigonometric identity: sin(-x) = -sin(x)
        // Given a domain of 0 <= x <= 2*pi, just negate the value if x > pi.
        // This allows the sin table size to be halved.

        int neg = (index & half) << 16;

        // All bits set if (pi/2 <= x), none set otherwise
        // Extracts the 15th bit from 'half'
        int mask = (index << 17) >> 31;

        // Trigonometric identity: sin(x) = sin(pi/2 - x)
        int pos = ((half + 1) & mask) + (index ^ mask);

        // Wrap the position in the table. Moving this down to immediately before the array access
        // seems to help the Hotspot compiler optimize the bit math better.
        pos &= (half - 1);

        // Fetch the corresponding value from the LUT and invert the sign bit as needed
        // This directly manipulate the sign bit on the float bits to simplify logic
        return Float.intBitsToFloat(SINE_TABLE_INT[pos] ^ neg);
    }
}
