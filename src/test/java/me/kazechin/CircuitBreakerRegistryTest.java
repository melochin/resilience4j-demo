package me.kazechin;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.vavr.control.Try;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static junit.framework.Assert.*;

public class CircuitBreakerRegistryTest {

	@Test
	public void testConfig() {
		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
				.failureRateThreshold(20)
				.slowCallRateThreshold(20)
				.slowCallDurationThreshold(Duration.ofSeconds(1))
				.build();

		assertEquals(20, config.getFailureRateThreshold(), 0.1);
		assertEquals(20d, config.getSlowCallRateThreshold(),0.1);
		assertEquals(Duration.ofSeconds(1), config.getSlowCallDurationThreshold());
	}

	@Test
	public void createCircuitBreak() {
		// 跟hystrix不一样，没有key来固定具体的配置
		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
				.failureRateThreshold(20)
				.slowCallRateThreshold(20)
				.slowCallDurationThreshold(Duration.ofSeconds(1))
				.build();

		CircuitBreaker breaker = CircuitBreakerRegistry.of(config).circuitBreaker("test");

		assertEquals(20, breaker.getCircuitBreakerConfig().getFailureRateThreshold(), 0.1);
	}

	@Test
	public void testCompletableFuture() throws InterruptedException, ExecutionException {

		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
				.slowCallDurationThreshold(Duration.ofMillis(100))
				.slowCallRateThreshold(1)
				.slidingWindowSize(1)
				.build();

		CircuitBreaker circuitBreaker = CircuitBreakerRegistry.of(config).circuitBreaker("");

		System.out.println(getNumberOne(circuitBreaker).get());
		System.out.println(getNumberOne(circuitBreaker).get());
		System.out.println(getNumberOne(circuitBreaker).get());
	}

	private CompletableFuture getNumberOne(CircuitBreaker circuitBreaker) throws InterruptedException {
		CompletableFuture completableFuture = new CompletableFuture();
		Supplier<CompletableFuture> supplier = CircuitBreaker.decorateCompletionStage(circuitBreaker, () -> completableFuture);

		CompletableFuture comFuture = Try.ofSupplier(supplier)
				.recover(throwable -> {
					CompletableFuture<Object> future = new CompletableFuture<>();
					future.completeExceptionally(new RuntimeException());
					return future;
				})
				.get();
		Thread.sleep(1000);
		completableFuture.complete(1);
		return comFuture;
	}


}
