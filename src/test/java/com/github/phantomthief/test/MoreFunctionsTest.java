package com.github.phantomthief.test;

import static com.github.phantomthief.util.MoreFunctions.catching;
import static com.github.phantomthief.util.MoreFunctions.runParallel;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.common.base.Supplier;

/**
 * @author w.vela
 */
class MoreFunctionsTest {

    @Test
    void testTrying() {
        assertTrue(catching(i -> function(i, Exception::new), 1) == null);
        assertTrue(catching(i -> function(i, IllegalArgumentException::new), 1) == null);
        assertTrue(catching(i -> function(i, null), 1).equals("1"));
    }

    private <X extends Throwable> String function(int i, Supplier<X> exception) throws X {
        if (exception != null) {
            X x = exception.get();
            throw x;
        } else {
            return i + "";
        }
    }

    @Test
    void testParallel() {
        List<Integer> list = Stream.iterate(1, i -> i + 1).limit(10000).collect(toList());
        runParallel(new ForkJoinPool(10), () -> list.stream().parallel() //
                .forEach(System.out::println));
    }
}
