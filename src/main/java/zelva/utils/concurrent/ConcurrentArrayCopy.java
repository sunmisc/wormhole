package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * @author ZelvaLea
 */
public class ConcurrentArrayCopy<E> {
    static final FIndex DEAD = new FIndex(null); // todo:
    static final int NCPU = Runtime.getRuntime().availableProcessors();
    static final int MIN_TRANSFER_STRIDE = 16;
    volatile Levels levels;

    public ConcurrentArrayCopy(int size) {
        this.levels = new QLevels(new Object[size]);
    }
    public ConcurrentArrayCopy(Object[] array) {
        int n = array.length;
        Object[] nodes = new Cell[n];
        for (int i = 0; i < n; ++i) {
            nodes[i] = new Cell(array[i]);
        }
        this.levels = new QLevels(nodes);
    }

    public int size() {
        return levels.array().length;
    }

    public E get(int i) {
        for (Object[] arr = levels.array();;) {
            Object o = arrayAt(arr, i);
            if (o == null || o == DEAD) {
                return null;
            } else if (o instanceof ForwardingPointer t) {
                arr = t.newCells;
            } else if (o instanceof Index f) {
                return (E) f.getValue();
            }
        }
    }
    public Object set(int i, Object element) {
        Object[] arr = levels.array();
        for (Object o; ; ) {
            if ((o = arrayAt(arr, i)) == element) {
                return element;
            } else if (o == null || o == DEAD) {
                if (casArrayAt(arr, i, o,
                        new Cell(element))) {
                    return null;
                }
            } else if (o instanceof ForwardingPointer f) {
                arr = transfer(f);
            } else if (element == null) {
                if (!(o instanceof FIndex) &&
                        casArrayAt(arr, i, o, null)) {
                    return null;
                }
            } else if (o instanceof Index n) {
                return n.setValue(element);
            }
        }
    }

    public void resize(int cap) {
        final Object[] nextArray = new Object[cap];
        ForwardingPointer fwd;
        for (Levels p;;) {
            if (((p = levels) instanceof QLevels) &&
                    p.fence() == cap) {
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
            int i = start;
            Object[] prev = oldCells;
            for (; i < end && i < fence; ++i) {
                Object[] shared = prev;
                for (Object o; ; ) {
                    if (sizeCtl >= fence) {
                        return;
                    } else if ((o = arrayAt(shared, i))
                            instanceof FIndex) {
                    } else if (o instanceof
                            ForwardingPointer f) {
                        if (f == this)
                            break;
                        shared = f.newCells;
                        if (f.sizeCtl > f.fence)
                            prev = shared;
                    } else if (trySwapSlot(o, i, shared, newCells)) {
                        break;
                    }
                }
            }
            int c = i - start, sz;
            do {
                if ((sz = sizeCtl) >= fence) {
                    return;
                }
            } while(!SIZECTL.weakCompareAndSet(this, sz, sz + c));
        }
        boolean trySwapSlot(Object o, int i,
                            Object[] oldCells, Object[] newCells) {
            Object c;
            if ((c = caeArrayAt(oldCells, i, o, new FIndex(
                    o instanceof Cell n ? n : DEAD))) == o) {
                // full fence
                setAt(newCells, i, o);
                setAt(oldCells, i, this);
                return true;
            } else return c == this;
        }
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
    interface Index {
        Object getValue();

        Object setValue(Object val);
    }

    record FIndex(Index main) implements Index {
        @Override public Object getValue() {return main.getValue();}
        @Override public Object setValue(Object val) {return main.setValue(val);}

        @Override public String toString() {return Objects.toString(getValue());}
    }
    static class Cell implements Index {
        volatile Object val;
        Cell(Object val) {
            this.val = val;
        }
        @Override public Object getValue() {return val;}
        @Override public Object setValue(Object val) {return VAL.getAndSet(this, val);}

        @Override public String toString() {return Objects.toString(val);}
    }

    record QLevels(Object[] array) implements Levels {} // inline type


    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle VAL;
    private static final VarHandle LEVELS;
    private static final VarHandle STRIDEINDEX;
    private static final VarHandle SIZECTL;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STRIDEINDEX = l.findVarHandle(ForwardingPointer.class, "strideIndex", int.class);
            SIZECTL = l.findVarHandle(ForwardingPointer.class, "sizeCtl", int.class);
            LEVELS = l.findVarHandle(ConcurrentArrayCopy.class, "levels", Levels.class);
            VAL = l.findVarHandle(Cell.class, "val", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}