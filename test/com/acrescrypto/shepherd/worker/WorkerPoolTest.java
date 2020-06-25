package com.acrescrypto.shepherd.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.acrescrypto.shepherd.TestTools.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.acrescrypto.shepherd.Program;
import com.acrescrypto.shepherd.taskset.SimpleTask;
import com.acrescrypto.shepherd.taskset.SimpleTaskSet;

public class WorkerPoolTest {
	
	WorkerPool pool;
	
	@BeforeEach
	public void beforeEach() {
		pool = new WorkerPool(new Program())
			     .run();
	}
	
	@Test
	public void testStartsWorkersWhenRunIsCalled() {
		waitFor(100,
				() -> pool.threadGroup().activeCount() == 1);
	}
	
	@Test
	public void testScalesWorkersUpWhenRequested() {
		for(int i = 1; i < 10; i++) {
			final int ii = i;
			pool.workers(i);
			
			waitFor(100, ()->pool.threadGroup().activeCount() == ii);
			holdFor( 20, ()->pool.threadGroup().activeCount() == ii);
		}
	}
	
	@Test
	public void testScalesWorkersDownWhenRequested() throws InterruptedException {
		for(int i = 10; i >= 0; i--) {
			final int ii = i;
			pool.workers(i);
			
			stabilize(10, 100, ()->pool.threadGroup().activeCount());
			waitFor(100, ()->pool.threadGroup().activeCount() == ii);
			holdFor( 20, ()->pool.threadGroup().activeCount() == ii);
		}
	}
	
	@Test
	public void testIssuesTasksToWorkersFromQueue() {
		SimpleTaskSet taskset = new SimpleTaskSet("test").pool(pool);
		AtomicInteger counter = new AtomicInteger(0);
		int numTasks = 100;
		
		pool.workers(8);
		for(int i = 0; i < numTasks; i++) {
			taskset.task(()->counter.incrementAndGet());
		}
		
		taskset.run();
		waitFor(100, ()->counter.get() == numTasks);
	}
	
	@Test
	public void testOrdersTasksByPriority() {
		SimpleTaskSet taskset = new SimpleTaskSet("test").pool(pool);
		int priority = 1;
		ConcurrentLinkedQueue<SimpleTask> tasks = new ConcurrentLinkedQueue<SimpleTask>();
		
		int numTasks = 100;
		for(int i = 0; i < numTasks; i++) {
			priority = (33*(priority + 63) + 1) % 127 - 63;
			taskset.task(new SimpleTask(
					taskset,
					"test",
					(task)->{
						tasks.add(task);
						task.finish();
					}
				).priority(priority));
		}
		
		pool.workers(1);
		taskset.run();
		waitFor(100, ()->tasks.size() == numTasks);
		
		priority = Integer.MAX_VALUE;
		for(SimpleTask task : tasks) {
			assertTrue(task.priority() <= priority);
			priority = task.priority();
		}
	}
	
	@Test
	public void testDoesNotCreateExtraThreadsWhenProcessingTasks() {
		SimpleTaskSet taskset = new SimpleTaskSet("test").pool(pool);
		
		pool.workers(8);
		stabilize(10, 100, ()->pool.threadGroup().activeCount() == pool.workers());

		for(int i = 0; i < 100; i++) {
			taskset.task(()->{});
		}
		taskset.run();
		holdFor  (     10, ()->pool.threadGroup().activeCount() == pool.workers());
		assertTrue(taskset.isFinished());
	}
	
	@Test
	public void testPassesExceptionsToHandler() {
		AtomicReference<Throwable> exception = new AtomicReference<>();
		Exception expectedException = new RuntimeException();
		
		pool.onException((exc)->exception.set(exc));
		
		new SimpleTaskSet("test")
			.pool(pool)
			.task(()->{ throw expectedException; })
			.run();
		waitFor(100, ()->exception.get() != null);
		assertEquals(expectedException, exception.get());
	}
	
	@Test
	public void testNamesThreadGroupAfterPoolName() {
		String name = "poolparty";
		pool.name(name);
		assertTrue(pool.threadGroup().getName().contains(name));
	}
	
	@Test
	public void testRespawnsWorkersInNewGroupWhenPoolNameChanged() {
		pool.workers(8);
		stabilize(10, 100, ()->pool.threadGroup().activeCount());
		assertEquals(pool.workers(), pool.threadGroup().activeCount());
		
		pool.name("newname");
		stabilize(10, 100, ()->pool.threadGroup().activeCount());
		assertEquals(pool.workers(), pool.threadGroup().activeCount());
	}
	
	@Test
	public void testCancelsWorkersInExistingGroupWhenPoolNameChanged() {
		ThreadGroup oldGroup = pool.threadGroup();
		
		pool.workers(8);
		stabilize(10, 100, ()->pool.threadGroup().activeCount());
		assertEquals(pool.workers(), pool.threadGroup().activeCount());
		
		pool.name("newname");
		stabilize(10, 100, ()->pool.threadGroup().activeCount());
		assertEquals(0, oldGroup.activeCount());
	}
}
