package sunmisc.utils.concurrent.ConcurrentEnumMap;

import sunmisc.utils.concurrent.maps.ConcurrentEnumMap;

import java.util.HashSet;

public class DistinctEntrySetElements {


    public static void main(final String[] args) {
        final var concurrentEnumMap =
                new ConcurrentEnumMap<>(TestEnum.class);

        concurrentEnumMap.put(TestEnum.A, "Un");
        concurrentEnumMap.put(TestEnum.B, "Deux");
        concurrentEnumMap.put(TestEnum.C, "Trois");

        final var entrySet = concurrentEnumMap.entrySet();

        final var hashSet = new HashSet<>(entrySet);

        System.out.println(entrySet.containsAll(hashSet));
        System.out.println(hashSet.containsAll(entrySet));

        if (!hashSet.equals(entrySet)) {
            throw new RuntimeException("Test FAILED: Sets are not equal.");
        } else if (hashSet.hashCode() != entrySet.hashCode()) {
            throw new RuntimeException("Test FAILED: Set's hashcodes are not equal.");
        }
    }
}
