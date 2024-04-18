package sunmisc.utils.world.math.base;

import sunmisc.utils.world.Unit;

public final class To64 extends Number {
    private final Unit a;

    public To64(Unit a) {
        this.a = a;
    }

    @Override
    public long longValue() {
        return a.get(a.length() - 1).longValue();
    }


    @Override
    public int intValue() {
        return a.get(a.length() - 1).intValue();
    }

    @Override
    public float floatValue() {
        return a.get(a.length() - 1).floatValue();
    }

    @Override
    public double doubleValue() {
        return a.get(a.length() - 1).doubleValue();
    }
}
