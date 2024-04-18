package sunmisc.utils.world;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

@FunctionalInterface
public interface Unit {

    default Number get(int index) {
        return values()[index];
    }


    Number[] values();


    default int length() {
        return values().length;
    }


    default byte[] bytes() {
        final Number[] longs = values();

        final int n = longs.length;
        final byte[] bytes = new byte[Long.BYTES * n];

        for (int i = 0 ; i < n; ++i) {
            var x = longs[i];
            if (x == null)
                break;
            AA.set(bytes, i, x.longValue());
        }
        return bytes;
    }

    VarHandle AA = MethodHandles.byteArrayViewVarHandle(
            long[].class, ByteOrder.BIG_ENDIAN);
}
