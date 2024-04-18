package sunmisc.utils.world.math.vectors;

import sunmisc.utils.world.Unit;

import java.util.concurrent.ThreadLocalRandom;

public class Rand implements Unit {
    private final int length;

    public Rand(int length) {
        this.length = length;
    }
    @Override
    public Number[] values() {
        return ThreadLocalRandom.current()
                .doubles(length)
                .boxed()
                .toArray(Number[]::new);
    }
}
