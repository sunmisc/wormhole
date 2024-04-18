package sunmisc.utils.world.math.vectors;

import sunmisc.utils.world.Unit;

public final class Vector implements Unit {

    private final Number[] vector;

    public Vector(Number... vector) {
        this.vector = vector;
    }

    @Override
    public Number[] values() {
        return vector;
    }
}
