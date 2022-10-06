package zelva.utils;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public final class UnsafeHolder {
    // Unsafe mechanics
    private static final sun.misc.Unsafe U;

    static {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (sun.misc.Unsafe) f.get(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new IllegalStateException("Cannot get Unsafe", e);
        }

    }

    private UnsafeHolder() {}

    public static Unsafe getUnsafe() {
        return U;
    }
}
