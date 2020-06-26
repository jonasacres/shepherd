package com.acrescrypto.shepherd.taskset;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.acrescrypto.shepherd.TestTools.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acrescrypto.shepherd.core.Program;

public class SimpleTaskSetTest {
	Program       program;
	SimpleTaskSet taskset;
	
	@BeforeEach
	public void beforeEach() {
		program = new Program().defaults();
		taskset = new SimpleTaskSet("test").pool(program.pool());
	}
	
	@AfterEach
	public void afterEach() throws TimeoutException, InterruptedException {
		program.stop(100);
	}
	
	@Test
	public void testEnqueuesTaskBySimpleTaskCallback() {
		taskset.task( (task) -> {});
		assertEquals(1, taskset.tasks().getFirst().size());
	}
	
	@Test
	public void testEnqueuesTaskByVoidCallback() {
		taskset.task( () -> {});
		assertEquals(1, taskset.tasks().getFirst().size());
	}
	
	@Test
	public void testEnqueuesTaskByNameAndSimpleTaskCallback() {
		taskset.task("taskname", (task) -> {});
		assertEquals(1, taskset.tasks().getFirst().size());
		assertEquals("taskname", taskset.tasks().getFirst().getFirst().name());
	}
	
	@Test
	public void testEnqueuesTaskByNameAndVoidCallback() {
		taskset.task("taskname", () -> {});
		assertEquals(1, taskset.tasks().getFirst().size());
		assertEquals("taskname", taskset.tasks().getFirst().getFirst().name());
	}
	
	@Test
	public void testEnqueuesTaskByInstance() {
		taskset.task(new SimpleTask(taskset, "taskname", ()->{}));
		assertEquals(1, taskset.tasks().getFirst().size());
		assertEquals("taskname", taskset.tasks().getFirst().getFirst().name());
	}
	
	@Test
	public void testEnqueuesThenBySimpleTaskCallback() {
		taskset.task( ()     -> {});
		taskset.then( (task) -> {});
		assertEquals(2, taskset.tasks().size());
		assertEquals(1, taskset.tasks().getLast().size());
	}
	
	@Test
	public void testEnqueuesThenByVoidCallback() {
		taskset.task( ()     -> {});
		taskset.then( ()     -> {});
		assertEquals(2, taskset.tasks().size());
		assertEquals(1, taskset.tasks().getLast().size());
	}
	
	@Test
	public void testEnqueuesThenByNameAndSimpleTaskCallback() {
		taskset.task(        ()     -> {});
		taskset.then("name", (task) -> {});
		assertEquals(2, taskset.tasks().size());
		assertEquals(1, taskset.tasks().getLast().size());
		assertEquals("name", taskset.tasks().getLast().getFirst().name());
	}
	
	@Test
	public void testEnqueuesThenByNameAndVoidCallback() {
		taskset.task(        ()     -> {});
		taskset.then("name", ()     -> {});
		assertEquals(2, taskset.tasks().size());
		assertEquals(1, taskset.tasks().getLast().size());
		assertEquals("name", taskset.tasks().getLast().getFirst().name());
	}
	
	@Test
	public void testEnqueuesThenByInstance() {
		taskset.task(        ()     -> {});
		taskset.then(new SimpleTask(taskset, "name", ()->{}));
		assertEquals(2, taskset.tasks().size());
		assertEquals(1, taskset.tasks().getLast().size());
		assertEquals("name", taskset.tasks().getLast().getFirst().name());
	}
	
	@Test
	public void testWaitForSignalInvokesCallbackWhenSignalReceived() {
		AtomicBoolean invoked = new AtomicBoolean();
		taskset
			.waitForSignal("signal", ()->invoked.set(true))
		    .run();
		
		waitFor( 10, ()->program.hub().handlersForSignal("signal").size() > 0);
		holdFor( 10, ()->invoked.get() == false);
		program.hub().signal("signal");
		waitFor(100, ()->invoked.get() ==  true);
	}
	
	@Test
	public void testWaitForSignalDoesNotRespondToSignalWhenRunHasNotBeenCalled() {
		taskset.waitForSignal("signal", ()->{});
		holdFor( 10, ()->program.hub().handlersForSignal("signal").size() == 0);
	}
	
	@Test
	public void testWaitForSignalWithArgumentInvokesCallbackWhenSignalReceivedWithMatchingArgument() {
		Object expectedArg = new Object();
		AtomicBoolean invoked = new AtomicBoolean();
		
		taskset
		  .waitForSignal("signal", expectedArg, (arg, task)->{
			invoked.set(true);
			task.finish();
		}).run();
		
		waitFor(100, ()->program.hub().handlersForSignal("signal").size() > 0);
		program.hub().signal("signal", expectedArg);
		
		waitFor(100, ()->invoked.get());
	}
	
	@Test
	public void testWaitForSignalWithArgumentDoesNotInvokeCallbackWhenSignalReceivedWithNonmatchingArgument() {
		Object expectedArg = new Object();
		AtomicBoolean invoked = new AtomicBoolean();
		
		taskset
		  .waitForSignal("signal", expectedArg, (arg, task)->{
			invoked.set(true);
			task.finish();
		}).run();
		
		waitFor(100, ()->program.hub().handlersForSignal("signal").size() > 0);
		program.hub().signal("signal", new Object());
		
		holdFor(20, ()->invoked.get() == false);
	}
	
	@Test
	public void testWaitForSignalInvokesCallbackWithArgument() {
		Object expectedArg = new Object();
		AtomicBoolean matched = new AtomicBoolean();
		
		taskset
		  .waitForSignal("signal", expectedArg, (arg, task)->{
			matched.set(arg == expectedArg);
			task.finish();
		}).run();
		
		waitFor(100, ()->program.hub().handlersForSignal("signal").size() > 0);
		program.hub().signal("signal", expectedArg);
		
		waitFor(100, ()->matched.get() == true);
	}
	
	@Test
	public void testWaitForSignalInvokesBlocksGateUntilCallbackComplete() {
		AtomicBoolean passedGate = new AtomicBoolean();
		
		taskset
			.waitForSignal("signal", ()->{})
			.then(()->passedGate.set(true))
			.run();
		
		waitFor(100, ()->program.hub().handlersForSignal("signal").size() > 0);
		holdFor( 20, ()->passedGate.get() == false);
		program.hub().signal("signal");
		waitFor(100, ()->passedGate.get() ==  true);
	}
	
	@Test
	public void testWaitsForSignalEnsuresHandlerRegisteredBeforeParallelTasksRun() {
		for(int i = 0; i < 100; i++) {
			SimpleTaskSet example      = new SimpleTaskSet("example " + i);
			AtomicBoolean signalCaught = new AtomicBoolean();
			
			example
				.pool(program.pool())
				.task        (           ()->program.hub().signal("signal"))
				.waitForSignal("signal", ()->signalCaught .set(true)       )
				.run();
			
			waitFor(100, ()->signalCaught.get());
		}
	}
	
	@Test
	public void testWaitsForSignalEnsuresHandlerRegisteredBeforeParallelTasksRunWithFullCallback() {
		for(int i = 0; i < 100; i++) {
			SimpleTaskSet example      = new SimpleTaskSet("example " + i);
			AtomicBoolean signalCaught = new AtomicBoolean();
			
			example
				.pool(program.pool())
				.task        (           (         )->program.hub().signal("signal"))
				.waitForSignal("signal", (arg, task)->signalCaught .set(true)       )
				.run();
			
			waitFor(100, ()->signalCaught.get());
		}
	}
	
	@Test
	public void testWaitsForSignalEnsuresHandlerRegisteredBeforeParallelTasksRunWithArgumentFilter() {
		for(int i = 0; i < 100; i++) {
			SimpleTaskSet example      = new SimpleTaskSet("example " + i);
			AtomicBoolean signalCaught = new AtomicBoolean();
			Object obj = new Object();
			
			example
				.pool(program.pool())
				.task        (                ()         ->program.hub().signal("signal", obj))
				.waitForSignal("signal", obj, (arg, task)->signalCaught .set(true)            )
				.run();
			
			waitFor(100, ()->signalCaught.get());
		}
	}
	
	@Test
	public void testWaitForSignalByItselfIsFine() {
		AtomicBoolean invoked = new AtomicBoolean();
		
		taskset
			.waitForSignal("signal", ()->invoked.set(true))
			.run();
		
		waitFor(100, ()->program.hub().handlersForSignal("signal").size() > 0);
		
		program.hub().signal("signal");
		waitFor(100, ()->invoked.get());
	}
	
	@Test
	public void testEnqueuesAfterBySimpleTaskCallback() {
		taskset.after((task)->{});
		assertEquals(1, taskset.after().size());
	}
	
	@Test
	public void testEnqueuesAfterByVoidCallback() {
		taskset.after(()->{});
		assertEquals(1, taskset.after().size());
	}
	
	@Test
	public void testEnqueuesAfterByNameAndSimpleTaskCallback() {
		taskset.after("name", (task)->{});
		assertEquals(1, taskset.after().size());
		assertEquals("name", taskset.after().getFirst().name());
	}
	
	@Test
	public void testEnqueuesAfterByNameAndVoidCallback() {
		taskset.after("name", ()->{});
		assertEquals(1, taskset.after().size());
		assertEquals("name", taskset.after().getFirst().name());
	}
	
	@Test
	public void testEnqueuesAfterByInstance() {
		taskset.after(new SimpleTask(taskset, "name", ()->{}));
		assertEquals(1, taskset.after().size());
		assertEquals("name", taskset.after().getFirst().name());
	}
	
	@Test
	public void testRunsTasksInParallel() {
		Object        notifier   = new Object();
		AtomicInteger counter    = new AtomicInteger();
		int           numWorkers = 8;
		
		program.pool().workers(8);
		waitFor(100, ()->program.pool().threadGroup().activeCount() == numWorkers);
		
		for(int i = 0; i < numWorkers; i++) {
			taskset
				.task(()->{
					counter.incrementAndGet();
					synchronized(notifier) {
						notifier.wait();
					}
					counter.incrementAndGet();
				});
		}
		
		taskset.run();
		
		waitFor(100, ()->counter.get() ==   numWorkers);
		holdFor(100, ()->counter.get() ==   numWorkers);
		synchronized(notifier) {
			notifier.notifyAll();
		}
		
		waitFor(100, ()->counter.get() == 2*numWorkers);
	}
	
	@Test
	public void testWaitsForTasksToCompleteBeforePassingGate() {
		AtomicBoolean ranA   = new AtomicBoolean(),
				      ranB   = new AtomicBoolean(),
				      ranC   = new AtomicBoolean(),
				      ranF   = new AtomicBoolean(),
				      allSet = new AtomicBoolean();
		program.pool().workers(4);
		waitFor(100, ()->program.pool().threadGroup().activeCount() == 4);
		
		taskset
			.task(()->ranA.set(true))
			.task(()->ranB.set(true))
			.task(()->{
				Thread.sleep(20);
				ranC.set(true);
		  }).gate()
			.task(()->{
				allSet.set(ranA.get() && ranB.get() && ranC.get());
				ranF.set(true);
		  }).run();
		
		waitFor(100, ()->ranF.get());
		assertTrue(allSet.get());
	}
	
	@Test
	public void testTasksAddedWithThenAddGate() {
		taskset
			.task(()->{})
			.then(()->{});
		
		assertEquals(2, taskset.pendingTasks().size());
	}
	
	@Test
	public void testRunningEmptyGroupsIsFine() {
		taskset
			.task(()->{})
			.gate()
			.gate()
			.task(()->{})
			.run();
		
		waitFor(100, ()->taskset.isFinished());
	}
	
	@Test
	public void testRunningWithNoTasksOrGatesIsFine() {
		taskset.run();
		
		waitFor(100, ()->taskset.isFinished());
	}
	
	@Test
	public void testRunningWithOnlyGatesIsFine() {
		taskset
			.gate()
			.gate()
			.run();
		
		waitFor(100, ()->taskset.isFinished());
	}
	
	@Test
	public void testRunningWithEmptyGatesAtEndIsFine() {
		taskset
			.task(()->{})
			.gate()
			.run();
		
		waitFor(100, ()->taskset.isFinished());
	}
	
	@Test
	public void testRunningWithEmptyGatesAtStartIsFine() {
		taskset
			.gate()
			.task(()->{})
			.run();
		
		waitFor(100, ()->taskset.isFinished());
	}
	
	@Test
	public void testRunsTasksAfterThenInParallel() {
		Object        notifier   = new Object();
		AtomicInteger counter    = new AtomicInteger();
		int           numWorkers = 8;
		
		program.pool().workers(8);
		waitFor(100, ()->program.pool().threadGroup().activeCount() == numWorkers);
		
		taskset
			.task(()->{})
			.then(()->{});
		
		for(int i = 0; i < numWorkers; i++) {
			taskset.task(()->{
				counter.incrementAndGet();
				synchronized(notifier) {
					notifier.wait();
				}
				counter.incrementAndGet();
			});
		}
		
		taskset.run();
		
		waitFor(100, ()->counter.get() ==   numWorkers);
		holdFor(100, ()->counter.get() ==   numWorkers);
		synchronized(notifier) {
			notifier.notifyAll();
		}
		
		waitFor(100, ()->counter.get() == 2*numWorkers);
	}
	
	@Test
	public void testInvokesAfterTasksWhenNormalTasksFinish() {
		Object notifier = new Object();
		AtomicBoolean startedAfter = new AtomicBoolean();
		
		program.pool().workers(2);
		waitFor(100, ()->program.pool().threadGroup().activeCount() == 2);
		
		taskset
			.task(()->{
				synchronized(notifier) {
					notifier.wait();
				}
		  }).after(()->{
			  startedAfter.set(true);
		  }).run();
		
		holdFor( 50, ()->startedAfter.get() == false);
		synchronized(notifier) {
			notifier.notifyAll();
		}
		
		waitFor(100, ()->startedAfter.get() ==  true);
	}

	@Test
	public void testDoesNotStartNewTasksAfterCancelInvoked() throws InterruptedException, BrokenBarrierException, TimeoutException {
		AtomicBoolean secondTaskInvoked = new AtomicBoolean();
		
		taskset
			.task(()->{
				taskset.cancel();
		  }).then(()->{
			  	secondTaskInvoked.set(true);
		  }).run();
		
		holdFor(50, ()->secondTaskInvoked.get() == false);
	}
	
	@Test
	public void testDoesNotInvokeAfterTasksWhenCancelInvoked() {
		AtomicBoolean afterRan = new AtomicBoolean();
		
		taskset
			.task(() -> {
			  taskset.cancel();
		  }).after(() -> {
			  afterRan.set(true);
		  }).run();
		
		waitFor(100, ()->taskset.isFinished());
		holdFor( 50, ()->afterRan.get() == false);
	}
	
	@Test
	public void testIsFinishedReturnsTrueAfterCancelInvoked() {
		taskset
			.task(() -> {
				taskset.cancel();
			  	Thread.sleep(1000);
		  }).run();
		
		waitFor(100, ()->taskset.isFinished());
	}
	
	@Test
	public void testInvokesAfterTasksWhenYieldInvoked() {
		AtomicBoolean afterInvoked = new AtomicBoolean();
		
		taskset
			.task(() -> {
			  taskset.yield();
		  }).after(()->{
			  afterInvoked.set(true);
		  }).run();
		
		waitFor(100, ()->afterInvoked.get());
	}
	
	@Test
	public void testDoesNotStartNewTasksAfterYieldInvoked() {
		AtomicBoolean invoked = new AtomicBoolean();
		
		taskset
			.task(()->taskset.yield())
			.then(()->invoked.set(true))
			.run();
		
		waitFor(100, ()->taskset.isFinished());
		holdFor( 10, ()->invoked.get() == false);
	}
	
	@Test
	public void testIsFinishedReturnsTrueAfterYieldInvoked() {
		taskset
		  .task(()->taskset.yield())
		  .run();
	
		waitFor(100, ()->taskset.isFinished());
	}
}