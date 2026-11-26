package io.github.alejorom.demandflow;

import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Test-only coordination: two transactions, a third observer, bounded gates, no ordering sleeps or retries. */
final class CatalogConcurrencySupport {
    interface TransactionBody { Object run() throws Exception; }
    record Outcome(Object value, Throwable failure) { }
    record Race(Outcome first, Outcome second) { }

    static Race commands(ConfigurableApplicationContext context, int port, Secrets secrets,
                         TransactionBody first, TransactionBody second) throws Exception {
        var factory = context.getBean(EntityManagerFactory.class);
        var manager = context.getBean(PlatformTransactionManager.class);
        return race(port, secrets, (pid, ready, release) -> {
            var tx = new TransactionTemplate(manager);
            return tx.execute(status -> {
                pid.set(backendPid(factory));
                Object result = invoke(first);
                ready.countDown();
                gate(release);
                return result;
            });
        }, pid -> new TransactionTemplate(manager).execute(status -> {
            pid.set(backendPid(factory));
            return invoke(second);
        }));
    }

    interface First { Object run(AtomicInteger pid, CountDownLatch ready, CountDownLatch release) throws Exception; }
    interface Second { Object run(AtomicInteger pid) throws Exception; }

    static Race race(int port, Secrets secrets, First first, Second second) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstPid = new AtomicInteger();
        var secondPid = new AtomicInteger();
        try {
            var a = pool.submit(() -> outcome(() -> first.run(firstPid, ready, release)));
            assertThat(ready.await(15, TimeUnit.SECONDS)).as("first transaction reached its uncommitted gate").isTrue();
            var b = pool.submit(() -> outcome(() -> second.run(secondPid)));
            try (var observer = connect(port, "demandflow_app", secrets.runtime);
                 var blocked = observer.prepareStatement("select ? = any(pg_blocking_pids(?))")) {
                blocked.setQueryTimeout(2);
                await().alias("second backend is actually blocked by first backend")
                        .pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(3))
                        .until(() -> {
                            if (secondPid.get() == 0) { return false; }
                            blocked.setInt(1, firstPid.get());
                            blocked.setInt(2, secondPid.get());
                            try (var result = blocked.executeQuery()) { return result.next() && result.getBoolean(1); }
                        });
                assertThat(firstPid.get()).isPositive().isNotEqualTo(secondPid.get());
            } finally {
                release.countDown();
            }
            // Future completion is outside each transaction boundary, including commit or rollback.
            return new Race(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    static void gate(CountDownLatch release) {
        try {
            if (!release.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Transaction gate deadline exceeded"); }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Transaction gate interrupted", interrupted);
        }
    }

    static int backendPid(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("select pg_backend_pid(), current_user"); var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(2)).isEqualTo("demandflow_app");
            return result.getInt(1);
        }
    }

    private static int backendPid(EntityManagerFactory factory) {
        return ((Number) EntityManagerFactoryUtils.getTransactionalEntityManager(factory)
                .createNativeQuery("select pg_backend_pid()", Integer.class).getSingleResult()).intValue();
    }

    private static Object invoke(TransactionBody command) {
        try { return command.run(); }
        catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("Test transaction failed", failure); }
    }

    private static Outcome outcome(Callable<Object> body) {
        try { return new Outcome(body.call(), null); }
        catch (Exception failure) { return new Outcome(null, failure); }
    }
}
