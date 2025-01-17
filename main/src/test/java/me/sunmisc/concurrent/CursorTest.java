package me.sunmisc.concurrent;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import sunmisc.utils.Cursor;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class CursorTest {

    @Test
    public void makeCursorAsIterator() {
        final List<Integer> source = new LinkedList<>();
        final List<Integer> result = new LinkedList<>();
        for (int i = 0; i < 16; ++i) {
            source.add(ThreadLocalRandom.current().nextInt());
        }
        new Cursor.IteratorAsCursor<>(
                source.iterator()
        ).forEach(result::add);
        MatcherAssert.assertThat(
                "The items from the source list must match",
                source,
                CoreMatchers.equalTo(result)
        );
    }
    @Test
    public void makeIteratorAsCursor() {
        final List<Integer> source = new LinkedList<>();
        final List<Integer> result = new LinkedList<>();
        for (int i = 0; i < 16; ++i) {
            source.add(ThreadLocalRandom.current().nextInt());
        }
        final Iterator<Integer> iterator =
                new Cursor.CursorAsIterator<>(
                        new Cursor.IteratorAsCursor<>(source.iterator())
                );
        iterator.forEachRemaining(result::add);
        MatcherAssert.assertThat(
                "The items from the source list must match",
                source,
                CoreMatchers.equalTo(result)
        );
    }
}
