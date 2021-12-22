package zelva.utils.nio;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

public interface ByteArrayView {
    VarHandle IVH = MethodHandles.byteArrayViewVarHandle(
            int[].class, ByteOrder.BIG_ENDIAN);
    VarHandle CVH = MethodHandles.byteArrayViewVarHandle(
            char[].class, ByteOrder.BIG_ENDIAN);
    VarHandle SVH = MethodHandles.byteArrayViewVarHandle(
            short[].class, ByteOrder.BIG_ENDIAN);
    VarHandle LVH = MethodHandles.byteArrayViewVarHandle(
            long[].class, ByteOrder.BIG_ENDIAN);
    VarHandle DVH = MethodHandles.byteArrayViewVarHandle(
            double[].class, ByteOrder.BIG_ENDIAN);
    VarHandle FVH = MethodHandles.byteArrayViewVarHandle(
            float[].class, ByteOrder.BIG_ENDIAN);
}
