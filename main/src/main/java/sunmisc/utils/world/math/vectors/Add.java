package sunmisc.utils.world.math.vectors;

import sunmisc.utils.world.Unit;

public final class Add implements Unit {

    private final Unit a, b;

    public Add(Unit a, Unit b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public Number get(int index) {
        return a.get(index).doubleValue() + b.get(index).doubleValue();
    }

    @Override
    public Number[] values() {
        int x = a.length(), y = b.length();
        if (x != y)
            throw new IllegalArgumentException("Vectors must have the same length");

        Number[] r1 = a.values(), r2 = b.values();
        final Number[] val = new Number[x];
        for (int i = 0; i < x; ++i)  {
            val[i] = r1[i].doubleValue() + r2[i].doubleValue();
        }
        return val;
    }
}
