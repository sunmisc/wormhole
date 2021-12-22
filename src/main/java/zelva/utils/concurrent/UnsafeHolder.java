package zelva.utils.concurrent;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public final class UnsafeHolder {
    // Unsafe mechanics
    private static final sun.misc.Unsafe U;

    static {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (Unsafe) f.get(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    private UnsafeHolder() {}


    public static sun.misc.Unsafe getUnsafe() {
        return U;
    }
}