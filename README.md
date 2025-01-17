# Wormhole Framework of Structures

<img alt="logo" src="https://github.com/sunmisc/MyConcurrencyWorld/assets/49918694/43fb0920-1fcb-441e-b72f-f64e42008f64" height="100px" />

This repository is primarily for fun rather than practicality. That said, it adheres to a personal code quality policy detailed below.

---

## Code Quality Policy

1. **No `synchronized`**
   - I favor minimalism. Objects should not have magical properties like `await`/`notify` or synchronization by object.
   - Instead, use `java.util.concurrent.lock`.

2. **No `volatile`**
   - I do not recommend using `volatile` or `VarHandle` unless absolutely necessary.
   - Prefer `java.util.concurrent.atomic`. However, in critical moments where reducing the footprint of an object is essential, `VarHandle volatile` can be used.

3. **No `ThreadLocal`**
   - The concept of `ThreadLocal` is inherently unsafe and prone to memory leaks.

> _This list may be expanded over time._

---

## Practical Examples

### Example: Using `Scalar`
```java
final Scalar<User, IOException> user = new RefreshLazy<>(
        new Scalar<>() {
            @Override
            public User value() throws IOException {
                return database.fetch("foo/bar");
            }
        },
        Duration.ofMinutes(10)
);
```

---

### Example: Using `ModifiableMemory`
```java
final AtomicReference<ModifiableMemory<Node<K, V>>> table =
        new AtomicReference<>(
                new SegmentsMemory<>(16)
        );
final AtomicInteger size = new AtomicInteger(0);

void put(final K key, final V value) {
   final int hash = key.hashCode();
   final ModifiableMemory<Node<K, V>> memory = this.table.get();
   final int n = memory.length();
   final int index = hash & (n - 1);
   final Node<K, V> newNode = new Node<>(key, value);
   final Node<K, V> node = memory.fetch(index);
   if (node == null) {
      final Node<K, V> witness = memory.compareAndExchange(
              index,
              null,
              newNode
      );
      if (witness != null) {
          witness.add(newNode);
      }
   } else {
      node.add(newNode);
   }
   final int inc = this.size.getAndIncrement();
   if (n <= inc) {
       this.table.compareAndSet(memory, memory.realloc(Math.max(n, inc + 1)));
   }
}
```

---

### Example: Using `Cursor`
```java
public final class ListCursor<E> implements Cursor<E> {
    private final List<E> origin;
    private final E item;
    private final int index;

    public ListCursor(final List<E> origin, final E item, final int index) {
        this.origin = origin;
        this.item = item;
        this.index = index;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public E element() {
        return this.item;
    }

    @Override
    public Cursor<E> next() {
        final int shift = this.index + 1;
        return shift < this.origin.size()
                ? new ListCursor<>(this.origin, this.origin.get(shift), shift)
                : Cursor.empty();
    }
}
public static void main(final String[] args) {
   final List<Integer> list = IntStream
           .range(0, 16)
           .boxed()
           .toList();
   new ListCursor<>(
           list,
           list.getFirst(),
           0
   ).forEach(e -> System.out.println(e));
}
```

---

## Requirements

- **Maven**: 3.3+
- **Java**: 21+

---

Feel free to contribute or provide feedback!

