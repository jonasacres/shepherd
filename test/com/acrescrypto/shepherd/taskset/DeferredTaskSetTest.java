package com.acrescrypto.shepherd.taskset;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.acrescrypto.shepherd.TestTools.*;

import com.acrescrypto.shepherd.core.Program;

public class DeferredTaskSetTest {
	Program         program;
	DeferredTaskSet taskset;
	AtomicInteger   counter;
	long            now;
	Object          argument;
	
	@BeforeEach
	public void beforeEach() {
		program  = testProgram();
		taskset  = new DeferredTaskSet("test").pool(program.pool());
		counter  = new AtomicInteger();
		now      = System.currentTimeMillis();
		argument = new Object();
	}
	
	@AfterEach
	public void afterEach() throws TimeoutException, InterruptedException {
		finishProgram(program);
	}
	
	@Test
	public void testEveryWithTaskCallbackRegistersRecurringTask() {
		taskset.every(1, "increment counter", (task)->counter.incrementAndGet());
		waitFor(()->counter.get() >= 2);
	}
	
	@Test
	public void testEveryWithVoidCallbackRegistersRecurringTask() {
		taskset.every(1, "increment counter", ()->counter.incrementAndGet());
		waitFor(()->counter.get() >= 2);
	}
	
	@Test
	public void testEveryTasksDontCallBackWhenTaskSetCancelled() {
		taskset.every(1, "increment counter", ()->counter.incrementAndGet());
		waitFor(()->counter.get() >= 2);
		
		taskset.cancel();
		stabilize(25, 100, ()->counter.get());
	}
	
	@Test
	public void testEveryTasksDontCallBackWhenTaskSetFinished() {
		taskset.every(1, "increment counter", ()->counter.incrementAndGet());
		waitFor(()->counter.get() >= 2);
		
		taskset.finish();
		stabilize(25, 100, ()->counter.get());
	}
	
	@Test
	public void testAtWithTaskCallbackRegistersDelayedTask() {
		long deadline = now + 20;
		taskset.at(deadline, "increment counter", (task)->counter.incrementAndGet());
		holdUntil(100,
				()->counter.get() == 0,
				()->System.currentTimeMillis() >= deadline
			);
		waitFor(50, ()->counter.get() == 1);
	}
	
	@Test
	public void testAtWithVoidCallbackRegistersDelayedTask() {
		long deadline = now + 20;
		taskset.at(deadline, "increment counter", ()->counter.incrementAndGet());
		holdUntil(100,
				()->counter.get() == 0,
				()->System.currentTimeMillis() >= deadline
			);
		waitFor(50, ()->counter.get() == 1);
	}
	
	@Test
	public void testAtTasksDontCallBackWhenTaskSetCancelled() {
		long deadline = now + 20;
		taskset.at(deadline, "increment counter", ()->counter.incrementAndGet());
		taskset.cancel();
		holdFor(50, ()->counter.get() == 0);
	}
	
	@Test
	public void testAtTasksDontCallBackWhenTaskSetFinished() {
		long deadline = now + 20;
		taskset.at(deadline, "increment counter", ()->counter.incrementAndGet());
		taskset.finish();
		holdFor(50, ()->counter.get() == 0);
	}
	
	@Test
	public void testDelayWithTaskCallbackRegistersDelayedTask() {
		taskset.delay(20, "increment counter", (task)->counter.incrementAndGet());
		holdUntil(100,
				()->counter.get() == 0,
				()->System.currentTimeMillis() >= now + 20
			);
		waitFor(50, ()->counter.get() == 1);
	}
	
	@Test
	public void testDelayWithVoidCallbackRegistersDelayedTask() {
		taskset.delay(20, "increment counter", ()->counter.incrementAndGet());
		holdUntil(100,
				()->counter.get() == 0,
				()->System.currentTimeMillis() >= now + 20
			);
		waitFor(50, ()->counter.get() == 1);
	}
	
	@Test
	public void testDelayTasksDontCallBackWhenTaskSetCancelled() {
		taskset.delay(20, "increment counter", ()->counter.incrementAndGet());
		taskset.cancel();
		holdFor(50, ()->counter.get() == 0);
	}
	
	@Test
	public void testDelayTasksDontCallBackWhenTaskSetFinished() {
		taskset.delay(20, "increment counter", ()->counter.incrementAndGet());
		taskset.finish();
		holdFor(50, ()->counter.get() == 0);
	}
	
	@Test
	public void testOnWithSignalCallbackRegistersSignalHandler() {
		taskset.on(
				"signal",
				"increment counter on signal",
				(task)->counter.incrementAndGet());
		program.hub().signal("signal");
		waitFor(()->counter.get() == 1);
	}
	
	@Test
	public void testOnWithVoidCallbackRegistersSignalHandler() {
		taskset.on(
				"signal",
				"increment counter on signal",
				()->counter.incrementAndGet());
		program.hub().signal("signal");
		waitFor(()->counter.get() == 1);
	}
	
	@Test
	public void testOnTasksDontCallBackWhenTaskSetCancelled() {
		taskset.on(
				"signal",
				"increment counter on signal",
				(task)->counter.incrementAndGet());
		program.hub().signal("signal");
		waitFor(()->counter.get() == 1);
		
		taskset.cancel();
		program.hub().signal("signal");
		holdFor(50, ()->counter.get() == 1);
	}
	
	@Test
	public void testOnTasksDontCallBackWhenTaskSetFinished() {
		taskset.on(
				"signal",
				"increment counter on signal",
				(task)->counter.incrementAndGet());
		program.hub().signal("signal");
		waitFor(()->counter.get() == 1);
		
		taskset.finish();
		program.hub().signal("signal");
		holdFor(50, ()->counter.get() == 1);
	}
	
	@Test
	public void testOnWithSignalCallbackAndArgumentRegistersSignalHandler() {
		taskset.on(
				"signal",
				argument,
				"increment counter on signal",
				(task)->counter.incrementAndGet());
		program.hub().signal("signal", argument);
		waitFor(()->counter.get() == 1);
	}
	
	@Test
	public void testOnWithSignalCallbackAndArgumentFiltersByArgument() {
		taskset.on(
				"signal",
				argument,
				"increment counter on signal",
				(task)->counter.incrementAndGet());
		program.hub().signal("signal");
		holdFor(50, ()->counter.get() == 0);
	}
	
	@Test
	public void testOnWithVoidCallbackAndArgumentRegistersSignalHandler() {
		taskset.on(
				"signal",
				argument,
				"increment counter on signal",
				()->counter.incrementAndGet());
		program.hub().signal("signal", argument);
		waitFor(()->counter.get() == 1);
	}
	
	@Test
	public void testOnWithVoidCallbackAndArgumentFiltersByArgument() {
		taskset.on(
				"signal",
				argument,
				"increment counter on signal",
				()->counter.incrementAndGet());
		program.hub().signal("signal");
		holdFor(50, ()->counter.get() == 0);
	}
	
	@Test
	public void testOnTasksWithArgumentDontCallBackWhenTaskSetCancelled() {
		taskset.on(
				"signal",
				argument,
				"increment counter on signal",
				(task)->counter.incrementAndGet());
		program.hub().signal("signal", argument);
		waitFor(()->counter.get() == 1);
		
		taskset.cancel();
		program.hub().signal("signal", argument);
		holdFor(50, ()->counter.get() == 1);
	}
	
	@Test
	public void testOnTasksWithArgumentDontCallBackWhenTaskSetFinished() {
		taskset.on(
				"signal",
				argument,
				"increment counter on signal",
				(task)->counter.incrementAndGet());
		program.hub().signal("signal", argument);
		waitFor(()->counter.get() == 1);
		
		taskset.finish();
		program.hub().signal("signal", argument);
		holdFor(50, ()->counter.get() == 1);
	}
}
