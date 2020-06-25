package com.acrescrypto.shepherd;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class TestTools {
	public static void stabilize(int intervalMs, int timeoutMs, Supplier<Object> test) {
		assertTimeoutPreemptively(
				Duration.ofMillis(timeoutMs),
				()->{
					Object lastValue   = null;
					long   lastChange  = System.currentTimeMillis();
					long   deadline    = lastChange + timeoutMs,
					       stableTime  = lastChange + intervalMs;
					
					while(System.currentTimeMillis() < Math.min(deadline, stableTime))
					{
						Object value = test.get();
						
						boolean nullityMatch = (value     == null)
								            == (lastValue == null);
						
						boolean bothNull     =  value     == null
								            && lastValue == null;
						
						boolean changed      = !nullityMatch
								            && !bothNull
								            && !value.equals(lastValue);
						
						lastValue = value;
						if(changed) {
							lastChange = System.currentTimeMillis();
						}
					}
				});
	}
	
	public static void waitFor(int timeoutMs, BooleanSupplier test) {
		assertTimeoutPreemptively(
				Duration.ofMillis(timeoutMs),
				()->{
					while(!test.getAsBoolean() && !Thread.currentThread().isInterrupted());
				});
	}
	
	public static void holdFor(int timeoutMs, BooleanSupplier test) {
		long endTime = System.currentTimeMillis() + timeoutMs;
		while(System.currentTimeMillis() < endTime) {
			assertTrue(test.getAsBoolean());
		}
	}
}
