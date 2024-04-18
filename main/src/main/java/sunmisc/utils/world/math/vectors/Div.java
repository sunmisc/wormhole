package sunmisc.utils.world.math.vectors;

import sunmisc.utils.world.Unit;

public final class Div implements Unit {

    private final Unit left, right;

    public Div(Unit left, Unit right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public Number[] values() {
        int x = left.length(), y = right.length();
        if (x != y)
            throw new IllegalArgumentException("Vectors must have the same length");

        Number[] r1 = left.values(), r2 = right.values();
        final Number[] val = new Number[x];
        for (int i = 0; i < x; ++i)  {
            val[i] = r1[i].doubleValue() / r2[i].doubleValue();
        }
        return val;
    }
}
