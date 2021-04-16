package me.kazechin;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.vavr.control.Try;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static junit.framework.Assert.*;

public class CircuitBreakerRegistryTest {

	private static final int DELAY_MILLS = 100;

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

		System.out.println(protectMethod(circuitBreaker, () -> getNumberOne()).get());
		System.out.println(protectMethod(circuitBreaker, () -> getNumberOne()).get());
		System.out.println(protectMethod(circuitBreaker, () -> getNumberOne()).get());
	}

	// 调用超时，仍然返回结果
	@Test
	public void shouldGetValueWhenSlowCall() throws ExecutionException, InterruptedException {
		// given
		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
				.slowCallDurationThreshold(Duration.ofMillis(10))
				.build();
		CircuitBreaker circuitBreaker = CircuitBreakerRegistry.of(config).circuitBreaker("");

		// when
		Integer res = (Integer) protectMethod(circuitBreaker, () -> getNumberOne()).get();

		// then
		assertEquals(1, res.intValue());
	}

	// 如果产生异常，从异常处理策略中恢复
	@Test
	public void shouldRecoverFromError() throws ExecutionException, InterruptedException {
		// given
		CircuitBreaker circuitBreaker = defaultCircuit();

		// when
		Integer res = (Integer) protectMethod(circuitBreaker, () -> getNumberOneWithError(-1)).get();

		// then
		assertEquals(-1, res.intValue());
	}

	// 熔断器打开状态，抛出拒绝调用异常，自行捕获异常处理
	@Test
	public void shouldRecoverFromCircuit() throws ExecutionException, InterruptedException {
		// given
		CircuitBreaker circuitBreaker = defaultCircuit();
		circuitBreaker.transitionToForcedOpenState();

		// when
		Integer result = ((CompletableFuture<Integer>) getTry(circuitBreaker, this::getNumberOne).get())
				// 处理拒绝调用异常 io.github.resilience4j.circuitbreaker.CallNotPermittedException
				.exceptionally(e -> 0)
				.get();

		// then
		assertEquals(0, result.intValue());
	}

	private CircuitBreaker defaultCircuit() {
		return CircuitBreakerRegistry.ofDefaults().circuitBreaker("");
	}

	private Try<CompletionStage<Integer>> getTry(CircuitBreaker circuitBreaker, Supplier<CompletionStage<Integer>> supplier) {
		return Try.ofSupplier(CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier));
	}


	private CompletableFuture protectMethod(CircuitBreaker circuitBreaker, Supplier callService) {

		Supplier<CompletableFuture<Integer>> supplier = CircuitBreaker.decorateCompletionStage(circuitBreaker, callService);

		CompletableFuture<Integer> stage = Try.ofSupplier(supplier)
				.recover(throwable -> {
					CompletableFuture<Integer> future = new CompletableFuture<>();
					future.completeExceptionally(new RuntimeException("test"));
					return future;
				})
				.getOrElseGet(throwable -> CompletableFuture.completedFuture(0));

		return stage;
	}

	private CompletableFuture getNumberOne() {
		CompletableFuture completableFuture = new CompletableFuture();
		// 模拟时间响应请求
		new Thread(() -> {
			try {
				Thread.sleep(DELAY_MILLS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			completableFuture.complete(1);
		}).start();

		return completableFuture;
	}

	private CompletableFuture getNumberOneWithError(int errorValue) {
		CompletableFuture completableFuture = new CompletableFuture();
		// 模拟时间响应请求
		new Thread(() -> {
			try {
				Thread.sleep(DELAY_MILLS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			completableFuture.complete(errorValue);
		}).start();

		return completableFuture;
	}

}
