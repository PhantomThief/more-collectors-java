package com.github.phantomthief.concurrent;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import com.github.phantomthief.util.ThrowableFunction;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.common.util.concurrent.UncheckedTimeoutException;

/**
 * @author w.vela
 * Created on 2018-06-25.
 */
public class MoreFutures {

    /**
     * @throws UncheckedTimeoutException if timeout occurred.
     * @throws java.util.concurrent.CancellationException if task was canceled.
     * @throws ExecutionError if a {@link Error} occurred.
     * @throws UncheckedExecutionException if a normal Exception occurred.
     */
    public static <T> T getUnchecked(@Nonnull Future<? extends T> future,
            @Nonnull Duration duration) {
        checkNotNull(duration);
        return getUnchecked(future, duration.toNanos(), NANOSECONDS);
    }

    /**
     * @throws UncheckedTimeoutException if timeout occurred.
     * @throws java.util.concurrent.CancellationException if task was canceled.
     * @throws ExecutionError if a {@link Error} occurred.
     * @throws UncheckedExecutionException if a normal Exception occurred.
     */
    public static <T> T getUnchecked(@Nonnull Future<? extends T> future, @Nonnegative long timeout,
            @Nonnull TimeUnit unit) {
        checkArgument(timeout > 0);
        checkNotNull(future);
        try {
            return getUninterruptibly(future, timeout, unit);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error) {
                throw new ExecutionError((Error) cause);
            } else {
                throw new UncheckedExecutionException(cause);
            }
        } catch (TimeoutException e) {
            throw new UncheckedTimeoutException(e);
        }
    }

    /**
     * @throws TryWaitFutureUncheckedException if not all calls are successful.
     */
    @Nonnull
    public static <F extends Future<V>, V> Map<F, V> tryWait(@Nonnull Iterable<F> futures,
            @Nonnull Duration duration) throws TryWaitFutureUncheckedException {
        checkNotNull(futures);
        checkNotNull(duration);
        return tryWait(futures, duration.toNanos(), NANOSECONDS);
    }

    /**
     * @throws TryWaitFutureUncheckedException if not all calls are successful.
     */
    @Nonnull
    public static <F extends Future<V>, V> Map<F, V> tryWait(@Nonnull Iterable<F> futures,
            @Nonnegative long timeout, @Nonnull TimeUnit unit)
            throws TryWaitFutureUncheckedException {
        checkNotNull(futures);
        checkArgument(timeout > 0);
        checkNotNull(unit);
        return tryWait(futures, timeout, unit, it -> it, TryWaitFutureUncheckedException::new);
    }

    /**
     * @throws TryWaitUncheckedException if not all calls are successful.
     */
    @Nonnull
    public static <K, V, X extends Throwable> Map<K, V> tryWait(@Nonnull Iterable<K> keys,
            @Nonnull Duration duration, @Nonnull ThrowableFunction<K, Future<V>, X> asyncFunc)
            throws X, TryWaitUncheckedException {
        checkNotNull(keys);
        checkNotNull(duration);
        checkNotNull(asyncFunc);
        return tryWait(keys, duration.toNanos(), NANOSECONDS, asyncFunc);
    }

    /**
     * @throws TryWaitUncheckedException if not all calls are successful.
     */
    @Nonnull
    public static <K, V, X extends Throwable> Map<K, V> tryWait(@Nonnull Iterable<K> keys,
            @Nonnegative long timeout, @Nonnull TimeUnit unit,
            @Nonnull ThrowableFunction<K, Future<V>, X> asyncFunc)
            throws X, TryWaitUncheckedException {
        return tryWait(keys, timeout, unit, asyncFunc, TryWaitUncheckedException::new);
    }

    @Nonnull
    private static <K, V, X extends Throwable> Map<K, V> tryWait(@Nonnull Iterable<K> keys,
            @Nonnegative long timeout, @Nonnull TimeUnit unit,
            @Nonnull ThrowableFunction<K, Future<V>, X> asyncFunc,
            @Nonnull Function<TryWaitResult, RuntimeException> throwing) throws X {
        checkNotNull(keys);
        checkArgument(timeout > 0);
        checkNotNull(unit);
        checkNotNull(asyncFunc);

        Map<Future<? extends V>, V> successMap = new LinkedHashMap<>();
        Map<Future<? extends V>, Throwable> failMap = new LinkedHashMap<>();
        Map<Future<? extends V>, TimeoutException> timeoutMap = new LinkedHashMap<>();
        Map<Future<? extends V>, CancellationException> cancelMap = new LinkedHashMap<>();

        long remainingNanos = unit.toNanos(timeout);
        long end = nanoTime() + remainingNanos;

        Map<Future<? extends V>, K> futureKeyMap = new IdentityHashMap<>();
        for (K key : keys) {
            checkNotNull(key);
            Future<V> future = asyncFunc.apply(key);
            checkNotNull(future);
            futureKeyMap.put(future, key);
            if (remainingNanos <= 0) {
                waitAndCollect(successMap, failMap, timeoutMap, cancelMap, future, 1L);
                continue;
            }
            waitAndCollect(successMap, failMap, timeoutMap, cancelMap, future, remainingNanos);
            remainingNanos = end - nanoTime();
        }

        TryWaitResult<K, V> result = new TryWaitResult<>(successMap, failMap, timeoutMap, cancelMap,
                futureKeyMap);

        if (failMap.isEmpty() && timeoutMap.isEmpty() && cancelMap.isEmpty()) {
            return result.getSuccess();
        } else {
            throw throwing.apply(result);
        }
    }

    private static <T> void waitAndCollect(Map<Future<? extends T>, T> successMap,
            Map<Future<? extends T>, Throwable> failMap,
            Map<Future<? extends T>, TimeoutException> timeoutMap,
            Map<Future<? extends T>, CancellationException> cancelMap, Future<? extends T> future,
            long thisWait) {
        try {
            T t = getUninterruptibly(future, thisWait, NANOSECONDS);
            successMap.put(future, t);
        } catch (CancellationException e) {
            cancelMap.put(future, e);
        } catch (TimeoutException e) {
            timeoutMap.put(future, e);
        } catch (ExecutionException e) {
            failMap.put(future, e.getCause());
        } catch (Throwable e) {
            failMap.put(future, e);
        }
    }
}
