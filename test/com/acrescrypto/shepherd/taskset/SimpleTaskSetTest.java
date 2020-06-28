package com.acrescrypto.shepherd.taskset;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
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
		
		waitFor(     ()->program.hub().handlersForSignal("signal").size() > 0);
		holdFor( 10, ()->invoked.get() == false);
		program.hub().signal("signal");
		waitFor(     ()->invoked.get() ==  true);
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
		
		waitFor(()->program.hub().handlersForSignal("signal").size() > 0);
		program.hub().signal("signal", expectedArg);
		
		waitFor(()->invoked.get());
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
		
		waitFor(    ()->program.hub().handlersForSignal("signal").size() > 0);
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
		
		waitFor(()->program.hub().handlersForSignal("signal").size() > 0);
		program.hub().signal("signal", expectedArg);
		
		waitFor(()->matched.get() == true);
	}
	
	@Test
	public void testWaitForSignalInvokesBlocksGateUntilCallbackComplete() {
		AtomicBoolean passedGate = new AtomicBoolean();
		
		taskset
			.waitForSignal("signal", ()->{})
			.then(()->passedGate.set(true))
			.run();
		
		waitFor(     ()->program.hub().handlersForSignal("signal").size() > 0);
		holdFor( 20, ()->passedGate.get() == false);
		program.hub().signal("signal");
		waitFor(     ()->passedGate.get() ==  true);
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
			
			waitFor(()->signalCaught.get());
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
			
			waitFor(()->signalCaught.get());
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
			
			waitFor(()->signalCaught.get());
		}
	}
	
	@Test
	public void testWaitsForSignalReceivesOnlyOneInstanceOfSignal() {
		AtomicInteger timesSeen = new AtomicInteger();
		
		taskset.pool().workers(2);
		waitFor(()->taskset.pool().threadGroup().activeCount() == 2);
		
		taskset
			.task(()->program.hub().signal("signal"))
			.task(()->program.hub().signal("signal"))
			.waitForSignal("signal", ()->timesSeen.incrementAndGet())
			.run();
		
		waitFor(()->taskset.isFinished());
		assertEquals(1, timesSeen);
	}
	
	@Test
	public void testExceptionsInSignalHandlerGoToTaskSetHandler() {
		AtomicBoolean sawException = new AtomicBoolean();
		
		taskset
			.onException((exc) -> sawException.set(true))
			.task(()->program.hub().signal("signal"))
			.waitForSignal("signal", ()->{throw new RuntimeException();})
			.run();
		
		waitFor(()->taskset.isFinished());
		assertTrue(sawException.get());
	}
	
	@Test
	public void testWaitForSignalByItselfIsFine() {
		AtomicBoolean invoked = new AtomicBoolean();
		
		taskset
			.waitForSignal("signal", ()->invoked.set(true))
			.run();
		
		waitFor(()->program.hub().handlersForSignal("signal").size() > 0);
		
		program.hub().signal("signal");
		waitFor(()->invoked.get());
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
	public void testRunsTasksInParallel() throws InterruptedException, BrokenBarrierException, TimeoutException {
		int           numWorkers = 8;
		AtomicInteger counter    = new AtomicInteger();
		CyclicBarrier barrier    = new CyclicBarrier(numWorkers + 1);
		
		program.pool().workers(8);
		waitFor(()->program.pool().threadGroup().activeCount() == numWorkers);
		
		for(int i = 0; i < numWorkers; i++) {
			taskset
				.task(()->{
					counter.incrementAndGet();
					barrier.await();
					counter.incrementAndGet();
				});
		}
		
		taskset.run();
		
		waitFor(     ()->counter.get() ==   numWorkers);
		holdFor(100, ()->counter.get() ==   numWorkers);
		barrier.await(1000, TimeUnit.MILLISECONDS);		
		waitFor(     ()->counter.get() == 2*numWorkers);
	}
	
	@Test
	public void testWaitsForTasksToCompleteBeforePassingGate() {
		AtomicBoolean ranA   = new AtomicBoolean(),
				      ranB   = new AtomicBoolean(),
				      ranC   = new AtomicBoolean(),
				      ranF   = new AtomicBoolean(),
				      allSet = new AtomicBoolean();
		program.pool().workers(4);
		waitFor(()->program.pool().threadGroup().activeCount() == 4);
		
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
		
		waitFor(()->ranF.get());
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
		
		waitFor(()->taskset.isFinished());
	}
	
	@Test
	public void testRunningWithNoTasksOrGatesIsFine() {
		taskset.run();
		waitFor(()->taskset.isFinished());
	}
	
	@Test
	public void testRunningWithOnlyGatesIsFine() {
		taskset
			.gate()
			.gate()
			.run();
		
		waitFor(()->taskset.isFinished());
	}
	
	@Test
	public void testRunningWithEmptyGatesAtEndIsFine() {
		taskset
			.task(()->{})
			.gate()
			.run();
		
		waitFor(()->taskset.isFinished());
	}
	
	@Test
	public void testRunningWithEmptyGatesAtStartIsFine() {
		taskset
			.gate()
			.task(()->{})
			.run();
		
		waitFor(()->taskset.isFinished());
	}
	
	@Test
	public void testRunsTasksAfterThenInParallel() throws InterruptedException, BrokenBarrierException, TimeoutException {
		int           numWorkers = 8;
		AtomicInteger counter    = new AtomicInteger();
		CyclicBarrier barrier    = new CyclicBarrier(numWorkers + 1);
		
		program.pool().workers(8);
		waitFor(()->program.pool().threadGroup().activeCount() == numWorkers);
		
		taskset
			.task(()->{})
			.then(()->{});
		
		for(int i = 0; i < numWorkers; i++) {
			taskset.task(()->{
				counter.incrementAndGet();
				barrier.await();
				counter.incrementAndGet();
			});
		}
		
		taskset.run();
		
		waitFor(     ()->counter.get() ==   numWorkers);
		holdFor(100, ()->counter.get() ==   numWorkers);
		barrier.await(1000, TimeUnit.MILLISECONDS);
		
		waitFor(     ()->counter.get() == 2*numWorkers);
	}
	
	@Test
	public void testInvokesAfterTasksWhenNormalTasksFinish() throws InterruptedException, BrokenBarrierException, TimeoutException {
		AtomicBoolean startedAfter = new AtomicBoolean();
		CyclicBarrier barrier = new CyclicBarrier(2);
		
		program.pool().workers(2);
		waitFor(     ()->program.pool().threadGroup().activeCount() == 2);
		
		taskset
			.task (()->barrier.await()       )
			.after(()->startedAfter.set(true))
			.run  ();
		
		holdFor( 50, ()->startedAfter.get() == false);
		barrier.await(1000, TimeUnit.MILLISECONDS);
		
		waitFor(     ()->startedAfter.get() ==  true);
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
		
		waitFor(     ()->taskset.isFinished());
		holdFor( 50, ()->afterRan.get() == false);
	}
	
	@Test
	public void testIsFinishedReturnsTrueAfterCancelInvoked() {
		taskset
			.task(() -> {
				taskset.cancel();
			  	Thread.sleep(10000);
		  }).run();
		
		waitFor(()->taskset.isFinished());
	}
	
	@Test
	public void testInvokesAfterTasksWhenYieldInvoked() {
		AtomicBoolean afterInvoked = new AtomicBoolean();
		
		taskset
			.task (() -> taskset.yield()       )
			.after(() -> afterInvoked.set(true))
		    .run();
		
		waitFor(()->afterInvoked.get());
	}
	
	@Test
	public void testDoesNotStartNewTasksAfterYieldInvoked() {
		AtomicBoolean invoked = new AtomicBoolean();
		
		taskset
			.task(()->taskset.yield()  )
			.then(()->invoked.set(true))
			.run();
		
		waitFor(     ()->taskset.isFinished()  );
		holdFor( 10, ()->invoked.get() == false);
	}
	
	@Test
	public void testIsFinishedReturnsTrueAfterYieldInvoked() {
		taskset
		  .task(()->taskset.yield())
		  .run();
	
		waitFor(()->taskset.isFinished());
	}
	
	@Test
	public void testDoesNotRunTasksIfParentCancelled() throws InterruptedException, BrokenBarrierException, TimeoutException {
		SimpleTaskSet parent   = new SimpleTaskSet("parent");
		AtomicBoolean invoked  = new AtomicBoolean();
		
		taskset
			.parent(parent)
			.task(()->parent .cancel()  )
			.then(()->invoked.set(true) )
			.run();
		
		waitFor(    ()->taskset.isFinished());
		holdFor(20, ()->invoked.get() == false);
	}
	
	@Test
	public void testDoesNotRunTasksIfParentFinished() throws InterruptedException, BrokenBarrierException, TimeoutException {
		SimpleTaskSet parent   = new SimpleTaskSet("parent");
		AtomicBoolean invoked  = new AtomicBoolean();
		
		taskset
			.parent(parent)
			.task(()->parent .finish() )
			.then(()->invoked.set(true))
			.run();
		
		waitFor(    ()->taskset.isFinished());
		holdFor(20, ()->invoked.get() == false);
	}
}
