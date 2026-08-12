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
 * {@code CollectorExecutorsConfiguration#collectorScheduler}: an inferred {@code close()} destroy method
 * would block {@link ConfigurableApplicationContext#close()} indefinitely while the cycle below is in
 * flight, and dropping the {@code @PreDestroy} would leave {@code collector-scheduler} alive after close.
 *
 * <p>The collector test task shares one JVM across all classes with cached, never-closed contexts, so two
 * isolation fixes are load-bearing, not style: the scheduler thread is asserted by captured identity (an
 * {@link AtomicReference}), never a JVM-wide name scan, which would alias onto same-named threads kept
 * alive by other cached contexts; and {@code spring.datasource.url} is forced to a private H2 instance via
 * {@link TestPropertyValues} — {@code SpringApplicationBuilder#properties(String...)} alone does NOT
 * override the profile-specific {@code application-test.yaml} value, since it is Boot's lowest-priority
 * "default properties" source. Without the override, close()'s real schema drop hits the {@code
 * jdbc:h2:mem:db} instance shared by every other test-profile context in the JVM.
 *
 * <p>The blocking {@link CollectionCycleRunner} replacement is registered via an
 * {@link org.springframework.context.ApplicationContextInitializer}, not a scanned {@code @Configuration}:
 * the latter would leak through {@code CollectorApplication}'s default component scan into every other
 * test that boots the full context, silently replacing their real {@code collectionCycleRunner} too.
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
        return new CollectionCycleRunner(null, null, null, null, null, null) {
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
