package sunmisc.utils.world.math.base;

import sunmisc.utils.world.Unit;

/**
 * Waiting for Vector API
 * I'm not at all happy with java primitives and BigInteger design
 * (along with performance too),
 * maybe things will go better with projects like Vector API, Valhalla, Liliput
 */
public final class Add implements Unit {

    private final Unit a, b;

    public Add(Unit a, Unit b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public Number[] values() {
        Number[] x = a.values(), y = b.values();
        // If x is shorter, swap the two arrays
        if (x.length < y.length) {
            final Number[] tmp = x;
            x = y;
            y = tmp;
        }
        int xIndex = x.length, yIndex = y.length;
        Number[] result = new Number[xIndex];
        long sum = 0;
        while (yIndex > 0) {
            final long z = x[--xIndex].longValue(),
                       q = y[--yIndex].longValue();
            result[xIndex] = (sum += z + q);
        }
        boolean carry = sum != 0;
        while (xIndex > 0 && carry) {
            long z = x[--xIndex].longValue() + 1;
            result[xIndex] = z;
            carry = z == 0;
        }
        if (carry) {
            final int n = result.length;
            Number[] bigger = new Number[n + 1];
            bigger[0] = 1;

            System.arraycopy(x, 0, bigger, 1, xIndex);
            System.arraycopy(result, 0, bigger,
                    xIndex + 1, n - xIndex);
            return bigger;
        } else {
            System.arraycopy(x, 0, result, 0, xIndex);
            return result;
        }
    }
}
