package com.acrescrypto.shepherd;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.acrescrypto.shepherd.core.Program;
import com.acrescrypto.shepherd.core.SignalHub;
import com.acrescrypto.shepherd.worker.WorkerPool;

public class TestTools {
	public static Program testProgram() {
		Program program = new Program();
		return program
			   .pool(new WorkerPool(program).workers(1))
			   .hub (new SignalHub (program))
			   .onException((exc)->{
				   program.globals().put("fatalException", exc);
				   exc.printStackTrace();
				});
	}
	
	public static void finishProgram(Program program) throws TimeoutException, InterruptedException {
		if(program.globals().containsKey("fatalException")) {
			fail((Throwable) program.globals().get("fatalException"));
		}
		
		program.stop(1000);
	}
	
	public static void stabilize(int intervalMs, int timeoutMs, Supplier<Object> test) {
		assertTimeoutPreemptively(
				Duration.ofMillis(timeoutMs),
				()->{
					Object lastValue   = null;
					long   now         = System.currentTimeMillis();
					long   deadline    = now + timeoutMs,
					       stableTime  = now + intervalMs;
					
					while(System.currentTimeMillis() < Math.min(deadline, stableTime))
					{
						Object value = test.get();
						
						boolean nullityMatch = (value     == null)
								            == (lastValue == null);
						
						boolean bothNull     =  value     == null
								            &&  lastValue == null;
						
						boolean changed      = !nullityMatch
								            || (     !bothNull
								                  && !value.equals(lastValue)
								               );
						
						lastValue = value;
						if(changed) {
							stableTime = System.currentTimeMillis() + intervalMs;
						}
					}
					
					if(System.currentTimeMillis() >= deadline) throw new TimeoutException();
				});
	}
	
	public static void waitFor(BooleanSupplier test) {
		waitFor(1000, test);
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
	
	public static void holdUntil(long timeoutMs, BooleanSupplier test, BooleanSupplier doneCondition) {
		long endTime = System.currentTimeMillis() + timeoutMs;
		while(!doneCondition.getAsBoolean()) {
			if(System.currentTimeMillis() > endTime) {
				fail("Timed out waiting for done condition");
			}
			
			if(!test.getAsBoolean()) {
				if(!doneCondition.getAsBoolean()) {
					fail("Hold condition failed");
				}
			}
		}
	}
}
