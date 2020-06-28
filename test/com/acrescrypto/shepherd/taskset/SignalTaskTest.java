package com.acrescrypto.shepherd.taskset;

import static com.acrescrypto.shepherd.TestTools.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.acrescrypto.shepherd.core.Program;

public class SignalTaskTest {
	SignalTask                    task,
	                              taskWithArg;
	DeferredTaskSet               taskset;
	Program                       program;
	String                        signal;
	AtomicInteger                 counter;
	Object                        argument;
	String                        name,
	                              nameWithArg;
	ConcurrentLinkedDeque<Object> receivedArguments;
	
	@BeforeEach
	public void beforeEach() {
		program           = testProgram();
		taskset           = new DeferredTaskSet("test").pool(program.pool());
		signal            = "signal";
		argument          = new Object();
		name              = "task";
		nameWithArg       = "task-with-arg";
		counter           = new AtomicInteger();
		receivedArguments = new ConcurrentLinkedDeque<>();
		
		task        = new SignalTask(
				name,
				taskset,
				signal,
				(tt)->counter.incrementAndGet());
		taskWithArg = new SignalTask(
				nameWithArg,
				taskset,
				signal,
				argument,
				(tt)->receivedArguments.add(tt.argument()));
	}
	
	@AfterEach
	public void afterEach() throws TimeoutException, InterruptedException {
		finishProgram(program);
	}
	
	public void waitForRegistration() {
		waitFor(()->program.hub().handlersForSignal(signal).size() > 0);
	}
	
	@Test
	public void testConstructWithoutArgumentSetsSignal() {
		assertEquals(signal, task.signal());
	}
	
	@Test
	public void testConstructWithoutArgumentSetsTaskSet() {
		assertEquals(taskset, task.taskset());
	}
	
	@Test
	public void testConstructWithoutArgumentSetsArgumentNull() {
		assertNull(task.argument());
	}
	
	@Test
	public void testConstructWithoutArgumentSetsHasArgumentFalse() {
		assertFalse(task.hasArgument());
	}
	
	@Test
	public void testConstructWithArgumentSetsSignal() {
		assertEquals(signal, taskWithArg.signal());
	}
	
	@Test
	public void testConstructWithArgumentSetsTaskSet() {
		assertEquals(taskset, taskWithArg.taskset());
	}
	
	@Test
	public void testConstructWithArgumentSetsArgument() {
		assertEquals(argument, taskWithArg.argument());
	}
	
	@Test
	public void testConstructWithArgumentSetsHasArgumentTrue() {
		assertTrue(taskWithArg.hasArgument());
	}
	
	@Test
	public void testTimesRemainingReturnsMinusOneIfTimesNotSet() {
		assertEquals(-1, task.timesRemaining());
	}
	
	@Test
	public void testDoesNotRegisterSignalHandlerFromConstructor() {
		assertEquals(0, program.hub().handlersForSignal(signal).size());
	}
	
	@Test
	public void testRegistersSignalWhenRun() {
		task.run();
		assertEquals(1, program.hub().handlersForSignal(signal).size());
	}
	
	@Test
	public void testInvokesLambdaFromSignalHandlerWithoutArgument() {
		task.run();
		waitForRegistration();
		
		program.hub().signal(signal);
		waitFor(()->counter.get() > 0);
		assertEquals(1, program.hub().handlersForSignal(signal).size());
	}
	
	@Test
	public void testInvokesLambdaFromSignalHandlerWithArgument() {
		taskWithArg.run();
		waitForRegistration();
		
		program.hub().signal(signal, argument);
		waitFor(()->receivedArguments.size() > 0);
		assertEquals(argument, receivedArguments.getFirst());
	}
	
	@Test
	public void testDoesNotInvokeLambdaFromSignalHandlerWithNonmatchingArgument() {
		taskWithArg.run();
		waitForRegistration();
		
		program.hub().signal(signal, "different argument");
		holdFor(25, ()->receivedArguments.size() == 0);
	}
	
	@Test
	public void testInvokesLambdaFromSignalHandlerWithForAnyArgumentIfNoArgumentSpecified() {
		task.run();
		waitForRegistration();
		
		program.hub().signal(signal, argument);
		waitFor(()->counter.get() > 0);
		assertEquals(1, program.hub().handlersForSignal(signal).size());
	}
	
	@Test
	public void testTimesRemainingReturnsRemainingCount() {
		// TODO: circle back to this; make sure the count updates
		task.times(2).run();
		waitForRegistration();
		
		assertEquals(2, task.timesRemaining());
		
		program.hub().signal(signal);
		waitFor(()->counter.get() == 1);
		assertEquals(1, task.timesRemaining());
		
		program.hub().signal(signal);
		waitFor(()->counter.get() == 2);
		assertEquals(0, task.timesRemaining());
	}
	
	@Test
	public void testUnregistersCallbackWhenTimesRemainingReachesZero() {
		task.times(1).run();
		waitForRegistration();
		
		program.hub().signal(signal);
		waitFor(()->counter.get() == 1);
		
		program.hub().signal(signal);
		assertEquals(0, program.hub().handlersForSignal(signal).size());
		holdFor(25, ()->counter.get() == 1);
	}
	
	@Test
	public void testTimesRemainingLimitIsThreadSafe() {
		int numWorkers          = Runtime.getRuntime().availableProcessors();
		int numLegalInvocations = 2;
		
		program.pool().workers(numWorkers);
		waitFor(()->program.pool().threadGroup().activeCount() == numWorkers);
		
		task.times(numLegalInvocations).run();
		waitForRegistration();
		
		SimpleTaskSet signaler = new SimpleTaskSet("signaler").pool(program.pool());
		for(int i = 0; i < 100; i++) {
			signaler.task(()->program.hub().signal(signal));
		}
		
		signaler.run();
		
		waitFor(()->signaler.isFinished());
		holdFor(25, ()->counter.get() == numLegalInvocations);
	}
}
