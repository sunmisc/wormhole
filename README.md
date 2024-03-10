A separate type of mentally retarded creativity

The author does not guarantee the correct operation of this shit (especially on
platforms other than x86)
(even though he conducts testing and benchmarks)
these are just sketches of ideas and thoughts

**Goal:**
* Provide more parallelism tools compared to standard jdk library.
* Provide more utilities

**Not goals:**
* Rewriting existing solutions in the standard jdk library

This library is just a sharp instrument, rushing
fill holes in the standard library
There's always some kind of obscure shit going on here,
using super-greedy optimization techniques, I often like
contradict oneself, confusing even experienced people intellectuals
Take these or those tools with a cool fakeHead, KNOWING WHAT THEY ARE
DO, select more profitable methods specifically for your case.

**I urge:**
* don't use ```synchronized``` You will make life easier for yourself and the openjdk project
synchronized by object entails a number of crutches and supports inside the jvm.
The Valhalla project is especially difficult with synchronized.
It’s hard for the Loom project too.
It is uncontrollable (if we do synchronized (this) - that's it) synchronized on an object gives it control over the lock.
The alternative is simple: ``java.util.concurrent.lock``
It provides proper locking mechanism design

* don`t use ```ThreadLocal``` use your own separate storage, for example: ``ConcurrentMap<Long, V>`` where Long is the threadId.
The ThreadLocal concept is terrible, it's insecure and causes performance problems

![image](https://github.com/sunmisc/MyConcurrencyWorld/assets/49918694/43fb0920-1fcb-441e-b72f-f64e42008f64)

