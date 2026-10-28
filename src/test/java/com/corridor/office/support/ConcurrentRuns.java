package com.corridor.office.support;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

public final class ConcurrentRuns {

    private ConcurrentRuns() {}

    public record Outcomes(int successes, List<Throwable> failures) {}

    public static Outcomes runTwiceConcurrently(Callable<?> task) throws InterruptedException {
        var start = new CountDownLatch(1);
        var failures = new ConcurrentLinkedQueue<Throwable>();
        var threads = IntStream.range(0, 2)
                .mapToObj(i -> Thread.ofVirtual().start(() -> {
                    try {
                        start.await();
                        task.call();
                    } catch (Exception exception) {
                        failures.add(exception);
                    }
                }))
                .toList();
        start.countDown();
        for (var thread : threads) {
            thread.join();
        }
        return new Outcomes(threads.size() - failures.size(), List.copyOf(failures));
    }
}
