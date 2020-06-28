package com.acrescrypto.shepherd.taskset;

import org.junit.jupiter.api.*;

import com.acrescrypto.shepherd.core.Program;

import static com.acrescrypto.shepherd.TestTools.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class RecurringTaskTest {
	Program         program;
	DeferredTaskSet taskset;
	RecurringTask   task;
	long            period;
	long            startTime;
	AtomicInteger   counter;
	
	@BeforeEach
	public void beforeEach() {
		period    = 25;
		startTime = System.currentTimeMillis();
		program   = testProgram();
		taskset   = new DeferredTaskSet("test").pool(program.pool());
		counter   = new AtomicInteger();
		task      = new RecurringTask("task", taskset, period, (tt)->counter.incrementAndGet());
	}
	
	@AfterEach
	public void afterEach() throws TimeoutException, InterruptedException {
		finishProgram(program);
	}
	
	@Test
	public void testConstructorSetsPeriod() {
		assertEquals(period, task.period());
	}
	
	@Test
	public void testConstructorSetsName() {
		assertEquals("task", task.name());
	}
	
	@Test
	public void testRunInvokesLambda() {
		task.run();
		assertEquals(1, counter.get());
	}
	
	@Test
	public void testPeriodReturnsCurrentPeriod() {
		assertEquals(period, task.period());
	}
	
	@Test
	public void testPeriodWithArgumentUpdatesCurrentPeriod() {
		task.period(1000);
		assertEquals(1000, task.period());
	}
	
	@Test
	public void testNotBeforeTimeSetBasedOnPeriod() {
		assertTrue(task.notBefore() >= startTime + period);
		assertTrue(task.notBefore()  < startTime + period + 100);
	}
	
	@Test
	public void testRunUpdatesNotBefore() {
		waitFor(()->System.currentTimeMillis() != task.notBefore());
		long oldFireTime = task.notBefore();
		long runTime     = System.currentTimeMillis();
		task.run();
		
		assertTrue(task.notBefore() > oldFireTime);
		assertTrue(task.notBefore() - runTime >= period);
	}
	
	@Test
	public void testResetUpdatesNotBefore() {
		waitFor(()->System.currentTimeMillis() != task.notBefore());
		long oldFireTime = task.notBefore();
		long resetTime   = System.currentTimeMillis();
		task.reset();
		
		assertTrue(task.notBefore() > oldFireTime);
		assertTrue(task.notBefore() - resetTime >= period);
	}
	
	@Test
	public void testRunReschedulesTaskIntoPool() {
		task.run();
		waitFor(()->counter.get() >= 2);
	}
}
