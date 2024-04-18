package sunmisc.utils.world.math;

import java.util.function.IntFunction;

public final class ArithmeticProgression implements IntFunction<Progression> {

    private final IntFunction<Progression> src;

    private ArithmeticProgression(IntFunction<Progression> src) {
        this.src = src;
    }

    public ArithmeticProgression(double a1, double d) {
        this(i -> new FArithmeticProgression(a1, d, i));
    }

    public ArithmeticProgression(int a1, int d) {
        this(i -> new IArithmeticProgression(a1, d, i));
    }

    @Override
    public Progression apply(int i) {
        return src.apply(i);
    }

    private record IArithmeticProgression(
            int a1, int d, int i
    ) implements Progression {

        @Override
        public Integer value() {
            return a1 + d * i - d;
        }

        @Override
        public Integer sum() {
            int y = 2 * a1 + d * i - d;
            return ((y & 1) == 0 ? i * (y >> 1) : (i >> 1) * y);
        }
    }


    private record FArithmeticProgression(
            double a1, double d, int i
    ) implements Progression {

        @Override
        public Number value() {
            return Math.fma(d, i, a1) - d;
        }

        @Override
        public Number sum() {
            double b = Math.fma(d, i, 2 * a1) - d;

            return (b / 2) * i;
        }
    }
}
