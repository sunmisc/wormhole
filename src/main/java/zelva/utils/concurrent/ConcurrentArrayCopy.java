package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * @author ZelvaLea
 */
public class ConcurrentArrayCopy<E> {
    static final Index<?> DEAD_NIL = new Index<>() {
        @Override public Object getValue() {return null;}
        @Override public Object setValue(Object val) {return null;}
        @Override public boolean cas(Object c, Object v) {return true;}
        @Override public String toString() {return "null";}
    };
    static final int NCPU = Runtime.getRuntime().availableProcessors();
    static final int MIN_TRANSFER_STRIDE = 16;
    volatile Levels levels;

    public ConcurrentArrayCopy(int size) {
        this.levels = new QLevels(new Object[size]);
    }
    public ConcurrentArrayCopy(E[] array) {
        int n; Object o;
        Object[] nodes = new Cell[n = array.length];
        for (int i = 0; i < n; ++i) {
            if ((o = array[i]) != null)
                nodes[i] = new Cell<>(o);
        }
        this.levels = new QLevels(nodes);
    }

    public int size() {
        return levels.array().length;
    }

    public E get(int i) {
        for (Object[] arr = levels.array();;) {
            Object o = arrayAt(arr, i);
            if (o == null) {
                return null;
            } else if (o instanceof ForwardingPointer t) {
                arr = t.newCells;
            } else if (o instanceof Index f) {
                return (E) f.getValue();
            }
        }
    }
    public E set(int i, Object element) {
        Objects.requireNonNull(element);
        Object[] arr = levels.array();
        for (Object o; ; ) {
            if ((o = arrayAt(arr, i)) == null) {
                if (casArrayAt(arr, i, null,
                        new Cell(element))) {
                    return null;
                }
            } else if (o == DEAD_NIL) {
            } else if (o instanceof ForwardingPointer f) {
                arr = transfer(f);
            } else if (o instanceof Index n) {
                return (E) n.setValue(element);
            }
        }
    }
    public E remove(int i) {
        Object[] arr = levels.array();
        for (Object o; ; ) {
            if ((o = arrayAt(arr, i)) == null
                    || o == DEAD_NIL) {
                return null;
            } else if (o instanceof ForwardingPointer f) {
                arr = transfer(f);
            } else if (o instanceof Cell n &&
                    casArrayAt(arr, i, o, null)) {
                return (E) n.getValue();
            }
        }
    }
    public boolean cas(int i, Object c, Object v) {
        Object[] arr = levels.array();
        for (Object o; ; ) {
            if ((o = arrayAt(arr, i)) == null) {
                if (c == null) {
                    if (v == null) {
                        return true;
                    } else if (casArrayAt(arr, i, o, new Cell(v))) {
                        return true;
                    }
                }
            } else if (o == DEAD_NIL) {
            } else if (o instanceof ForwardingPointer f) {
                arr = transfer(f);
            } else if (o instanceof Index n) {
                return n.getValue() == c && n.cas(c, v);
            }
        }
    }

    public void resize(int length) {
        final Object[] nextArray = new Object[length];
        ForwardingPointer fwd;
        for (Levels p;;) {
            if (((p = levels) instanceof QLevels) &&
                    p.fence() == length) {
                return;
            } else if (LEVELS.weakCompareAndSet(this, p,
                    fwd = new ForwardingPointer(p, nextArray))) {
                break;
            }
        }
        LEVELS.compareAndSet(this, fwd, new QLevels(transfer(fwd)));
    }
    private Object[] transfer(ForwardingPointer a) {
        for (int i, ls; ; ) {
            if ((i = a.strideIndex) >= a.fence) {
                // recheck before commit and help
                a.transferChunk(0, i);
                return a.newCells;
            } else if (STRIDEINDEX.weakCompareAndSet(a, i,
                    ls = i + a.stride)) {
                a.transferChunk(i, ls);
            }
            if (levels instanceof ForwardingPointer f) {
                a = f;
            }
        }
    }

    static final class ForwardingPointer implements Levels {
        final int fence, stride;
        final Object[] oldCells, newCells;
        volatile int strideIndex, sizeCtl;

        ForwardingPointer(Levels prev, Object[] newCells) {
            this.oldCells = prev.array(); this.newCells = newCells;
            int n = Math.min(prev.fence(), newCells.length);
            this.fence = n;
            // threshold
            this.stride = Math.max((n >>> 2) / NCPU, Math.min(n, MIN_TRANSFER_STRIDE));
        }
        @Override public Object[] array() {return oldCells;}
        @Override public int fence() {return fence;}

        void transferChunk(int start, int end) {
            int i = start, bound = fence;
            Object[] prev = oldCells;
            for (; i < end && i < bound; ++i) {
                Object[] shared = prev;
                for (Object o; ; ) {
                    if (sizeCtl >= bound) {
                        return;
                    } else if ((o = arrayAt(shared, i)) == DEAD_NIL
                            || o instanceof DeadIndex) {
                    } else if (o instanceof
                            ForwardingPointer f) {
                        if (f == this)
                            break;
                        shared = f.newCells;
                        if (f.sizeCtl > f.fence) {
                            prev = shared;
                            bound = Math.min(bound, f.fence);
                        }
                    } else if (trySwapSlot(o, i, shared, newCells)) {
                        break;
                    }
                }
            }
            int c = i - start, sz;
            do {
                if ((sz = sizeCtl) >= bound) {
                    return;
                }
            } while(!SIZECTL.weakCompareAndSet(this, sz, sz + c));
        }
        boolean trySwapSlot(Object o, int i,
                            Object[] oldCells, Object[] newCells) {
            Object c;
            if ((c = caeArrayAt(oldCells, i, o,
                    o instanceof Cell n ? new DeadIndex(n) : DEAD_NIL))
                    == o) {
                // full fence
                setAt(newCells, i, o);
                setAt(oldCells, i, this);
                return true;
            } else return c == this;
        }
    }
    @Override
    public int hashCode() {
        Object[] arr = levels.array();
        int result = 1, n = arr.length;
        for (int i = 0; i < n; i++) {
            for (Object f = arrayAt(arr, i); ; ) {
                if (f instanceof ForwardingPointer t) {
                    f = arrayAt(t.newCells, i);
                } else if (f instanceof Index o) {
                    Object val = o.getValue();
                    result = 31 * result + (val == null ? 0 : val.hashCode());
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        Object[] arr = levels.array();
        if (arr.length == 0)
            return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; ; ) {
            for (Object f = arrayAt(arr, i); ; ) {
                if (f instanceof ForwardingPointer t) {
                    f = arrayAt(t.newCells, i);
                    continue;
                }
                sb.append(f);
                if (++i == arr.length) // last
                    return sb.append(']').toString();
                sb.append(", ");
                break;
            }
        }
    }
    static Object arrayAt(Object[] arr, int i) {
        return AA.getVolatile(arr, i);
    }
    static void setAt(Object[] arr, int i, Object v) {
        AA.setVolatile(arr,i,v);
    }
    static boolean casArrayAt(Object[] arr, int i, Object c, Object v) {
        return AA.weakCompareAndSet(arr,i,c,v);
    }
    static Object caeArrayAt(Object[] arr, int i, Object c, Object v) {
        return AA.compareAndExchange(arr,i,c,v);
    }

    @FunctionalInterface
    interface Levels {
        Object[] array();
        default int fence() {return array().length;}
    }
    interface Index<E> {
        E getValue();

        E setValue(E val);

        boolean cas(E c, E v);
    }

    record DeadIndex<E>(Index<E>main) implements Index<E> {
        @Override public E getValue() {return main.getValue();}
        @Override public E setValue(E val) {return main.setValue(val);}

        @Override public boolean cas(E c, E v) {return main.cas(c,v);}

        @Override public String toString() {return Objects.toString(getValue());}
    }
    static class Cell<E> implements Index<E> {
        volatile E val;
        Cell(E val) {
            this.val = val;
        }
        @Override public E getValue() {return val;}
        @Override public E setValue(E val) {return (E) VAL.getAndSet(this, val);}

        @Override public boolean cas(E c, E v) {return VAL.compareAndSet(this, c,v);}

        @Override public String toString() {return Objects.toString(val);}

        private static final VarHandle VAL;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                VAL = l.findVarHandle(Cell.class, "val", Object.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    record QLevels(Object[] array) implements Levels {} // inline type


    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle LEVELS;
    private static final VarHandle STRIDEINDEX;
    private static final VarHandle SIZECTL;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STRIDEINDEX = l.findVarHandle(ForwardingPointer.class, "strideIndex", int.class);
            SIZECTL = l.findVarHandle(ForwardingPointer.class, "sizeCtl", int.class);
            LEVELS = l.findVarHandle(ConcurrentArrayCopy.class, "levels", Levels.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}