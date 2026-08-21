/**
 * The MIT License
 *
 * Copyright (c) 2023- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2016-2023 Finnish Digital Agency
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.catalog.collector;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.collector.tasks.CollectionCycleRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-context guard for the {@code destroyMethod = ""} choice on
 * {@code CollectorExecutorsConfiguration#collectorScheduler}: an inferred {@code close()} would block
 * {@link ConfigurableApplicationContext#close()} while a cycle is in flight, and dropping the
 * {@code @PreDestroy} would leave {@code collector-scheduler} alive after close.
 *
 * <p>Three details keep this test from disturbing the other contexts cached in the same JVM:
 * <ul>
 *   <li>the scheduler thread is asserted by captured identity ({@link AtomicReference}), never by a
 *       JVM-wide name scan, which would alias onto same-named threads in other cached contexts;</li>
 *   <li>{@code spring.datasource.url} is overridden through {@link TestPropertyValues}, because
 *       {@code SpringApplicationBuilder#properties(String...)} does not override
 *       {@code application-test.yaml}; without it the schema drop on close hits the H2 instance shared by
 *       every other test-profile context;</li>
 *   <li>the blocking {@link CollectionCycleRunner} is registered through an
 *       {@link org.springframework.context.ApplicationContextInitializer}, so it is not picked up by the
 *       component scan of every other test that boots the full context.</li>
 * </ul>
 */
class CollectorGracefulShutdownIntegrationTest {

    @Test
    void contextCloseTerminatesSchedulerWithinGraceBoundAndLeavesNoSchedulerThreadAlive() throws InterruptedException {
        CountDownLatch cycleStarted = new CountDownLatch(1);
        AtomicReference<Thread> schedulerThread = new AtomicReference<>();

        ConfigurableApplicationContext context = new SpringApplicationBuilder(CollectorApplication.class)
                .profiles("test", "general-testdata")
                .initializers(applicationContext -> {
                    TestPropertyValues.of("spring.datasource.url=jdbc:h2:mem:shutdown-guard;NON_KEYWORDS=TYPE,VALUE")
                            .applyTo(applicationContext);
                    ((GenericApplicationContext) applicationContext).registerBean("collectionCycleRunner",
                            CollectionCycleRunner.class, () -> blockingCollectionCycleRunner(cycleStarted, schedulerThread));
                })
                .run();
        try {
            assertTrue(cycleStarted.await(10, TimeUnit.SECONDS), "collection cycle should have started before shutdown");
            assertNotNull(schedulerThread.get(), "collection cycle should have captured the collector-scheduler thread");

            long startNanos = System.nanoTime();
            context.close();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

            assertTrue(elapsed.toSeconds() < 25, "context close should complete within the shutdown grace bound, took " + elapsed);

            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertFalse(schedulerThread.get().isAlive(), "collector-scheduler thread should not survive context close"));
        } finally {
            context.close();
        }
    }

    private static CollectionCycleRunner blockingCollectionCycleRunner(CountDownLatch cycleStarted,
            AtomicReference<Thread> schedulerThread) {
        return new CollectionCycleRunner(null, null, null, null, null, null, null) {
            @Override
            public void run() {
                schedulerThread.set(Thread.currentThread());
                cycleStarted.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
    }
}
