package sunmisc.utils.math;

public class QArithmeticProgression implements ArithmeticProgression {

    private final ArithmeticProgression src;

    public QArithmeticProgression(double a1, double d) {
        this.src = new FArithmeticProgression(a1, d);
    }
    public QArithmeticProgression(int a1, int d) {
        this.src = new IArithmeticProgression(a1, d);
    }

    @Override
    public Number sum(int i) {
        return src.sum(i);
    }

    @Override
    public Number of(int i) {
        return src.of(i);
    }

    private record IArithmeticProgression(
            int a1, int d
    ) implements ArithmeticProgression {

        @Override
        public Integer of(int i) {
            return a1 + d * i - d;
        }

        @Override
        public Integer sum(int i) {
            int y = 2 * a1 + d * i - d;
            return ((y & 1) == 0 ? i * (y >> 1) : (i >> 1) * y);
        }
    }


    private record FArithmeticProgression(
            double a1, double d
    ) implements ArithmeticProgression {

        @Override
        public Number of(int i) {
            return Math.fma(d, i, a1) - d;
        }

        @Override
        public Number sum(int i) {
            double b = Math.fma(d, i, 2 * a1) - d;

            return (b / 2) * i;
        }
    }
}
