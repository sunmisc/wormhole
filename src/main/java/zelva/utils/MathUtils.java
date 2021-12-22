package zelva.utils;

public final class MathUtils {
    public static final double _2PI = 2 * Math.PI;

    private MathUtils() {}

    public static boolean isEven(int a) {
        return (a & 1) == 0;
    }
    public static boolean isEven(long a) {
        return (a & 1) == 0;
    }
}
