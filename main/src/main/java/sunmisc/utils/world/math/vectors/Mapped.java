package sunmisc.utils.world.math.vectors;

import sunmisc.utils.world.Unit;

import java.util.function.UnaryOperator;

public final class Mapped implements Unit {

    private final Unit origin;
    private final UnaryOperator<Number> function;

    public Mapped(Unit origin, UnaryOperator<Number> function) {
        this.origin = origin;
        this.function = function;
    }


    @Override
    public Number[] values() {
        Number[] val = origin.values();
        Number[] result = new Number[val.length];

        for (int i = 0, n = val.length; i < n; ++i) {
            result[i] = function.apply(val[i]);
        }
        return result;
    }
}
