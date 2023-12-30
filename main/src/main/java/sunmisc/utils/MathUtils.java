package sunmisc.utils;

/**
 * @author Sunmisc Unsafe
 */
@SuppressWarnings("forRemoval")
public final class MathUtils {

    private MathUtils() {}

    public static int sumArithmeticProgression(int n) {
        int y = n + 1;
        return (y & 1) == 0 ? n * (y >> 1) : (n >> 1) * y;
    }

}
