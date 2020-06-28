package com.acrescrypto.shepherd.taskset;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.acrescrypto.shepherd.TestTools.*;

import org.junit.jupiter.api.*;

import com.acrescrypto.shepherd.core.Program;

public class DelayedTaskTest {
	DeferredTaskSet taskset;
	DelayedTask     task;
	Program         program;
	long            fireTime;
	AtomicBoolean   invoked;
	
	@BeforeEach
	public void beforeEach() {
		program  = new Program().defaults();
		taskset  = new DeferredTaskSet("test").pool(program.pool());
		fireTime = System.currentTimeMillis() + 100;
		invoked  = new AtomicBoolean();
		task     = new DelayedTask("task", taskset, fireTime, (tt)->invoked.set(true));
	}
	
	@AfterEach
	public void afterEach() throws TimeoutException, InterruptedException {
		program.stop(1000);
	}
	
	@Test
	public void testConstructorSetsNotBeforeTime() {
		assertEquals(fireTime, task.notBefore());
	}
	
	@Test
	public void testTasksetReturnsTaskset() {
		assertEquals(taskset, task.taskset());
	}
	
	@Test
	public void testRunInvokesLambda() {
		task.run();
		assertTrue(invoked.get());
	}
	
	@Test
	public void testWorkerPoolExecutesAtSpecifiedTime() {
		program.pool().addTask(task);
		holdUntil(1000,
				()->invoked.get() == false || System.currentTimeMillis() >= fireTime,
				()->System.currentTimeMillis() >= fireTime);
	}
}
