package com.acrescrypto.shepherd.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static com.acrescrypto.shepherd.TestTools.*;

import com.acrescrypto.shepherd.core.Program;
import com.acrescrypto.shepherd.taskset.SimpleTask;
import com.acrescrypto.shepherd.taskset.SimpleTaskSet;
import com.acrescrypto.shepherd.taskset.Task;

public class WorkerTest {
	class WorkerPoolStubber extends WorkerPool {
		Worker    finishedWorker;
		boolean   shouldCancel;
		boolean   evilException;
		Throwable exception;
		Task<?>   task;
		Class<?>  expectedException;
		
		public WorkerPoolStubber() {
			super(new Program());
			this.exceptionHandler = (exc) -> {
				if(!exc.getClass().equals(expectedException)) {
					exc.printStackTrace();
					evilException = true;
				}
				
				exception = exc;
			};
		}
		
		@Override
		public void workerFinished(Worker worker) {
			finishedWorker = worker;
		}
		
		@Override
		protected boolean isThisWorkerAllowedToContinue() {
			return !shouldCancel;
		}
		
		@Override
		public Task<?> dequeueTask() {
			Task<?> t = task;
			if(t != null) {
				task = null;
			} else {
				try { Thread.sleep(1); }
				catch (InterruptedException e) {}
			}
			
			return t;
		}
		
		public void runTask(Task<?> task) {
			this.task = task;
		}
	}
	
	WorkerPoolStubber pool;
	Worker            worker;
	SimpleTaskSet     taskset;
	
	
	protected void waitForWorkerStart() {
		waitFor(100, ()->pool.threadGroup().activeCount() == 1);
	}
	
	protected void waitForWorkerStop() {
		waitFor(100, ()->pool.threadGroup().activeCount() == 0);
	}
	
	@BeforeEach
	public void beforeEach() {
		pool    = new WorkerPoolStubber();
		taskset = new SimpleTaskSet("test").pool(pool);
		worker  = new Worker(pool).run();
	}
	
	@AfterEach
	public void afterEach() throws Throwable {
		pool.expectedException = InterruptedException.class;
		if(pool.evilException) throw pool.exception;
		
		pool.shouldCancel = true;
		worker.thread.interrupt();
		try {
			waitForWorkerStop();
		} catch(Throwable exc) {
			pool.threadGroup.list();
			fail("Outstanding worker threads remaining");
		}
	}
	
	@Test
	public void testBeginsRunloopOnNewThreadWhenRunCalled() throws Exception {
		waitForWorkerStart();
		holdFor( 10, ()->pool.threadGroup().activeCount() == 1);
	}
	
	@Test
	public void testRunloopDequeuesTasks() {
		SimpleTask task = new SimpleTask(taskset, "task", ()->{});
		pool.runTask(task);
		waitFor(100, ()->task.isFinished());
	}
	
	@Test
	public void testExitsRunloopWhenCancelled() {
		waitForWorkerStart();
		pool.shouldCancel = true;
		waitForWorkerStop();
	}
	
	@Test
	public void testNotifiesPoolWhenWorkerExitsRunloop() {
		pool.shouldCancel = true;
		waitFor(100, ()->pool.finishedWorker == worker);
	}
	
	@Test
	public void testRefersExceptionsToPoolExceptionHandler() {
		pool.expectedException = RuntimeException.class;
		pool.runTask(new SimpleTask(taskset, "task", ()->{
			throw new RuntimeException("test");
		}));
		
		waitFor(100, ()->pool.exception != null);
	}
	
	@Test
	public void testSetsActiveTaskWhenInvokingTaskCallback() {
		Task<?> task = new SimpleTask(taskset, "task", ()->{
			synchronized(worker) {
				worker.wait();
			}
		});
		
		pool.runTask(task);
		waitFor(100, ()->worker.activeTask() == task);
		synchronized(worker) { worker.notifyAll(); }
	}
	
	@Test
	public void testClearsActiveTaskWhenTaskCallbackComplete() throws InterruptedException {
		Task<?> task = new SimpleTask(taskset, "task", ()->{
			synchronized(worker) {
				worker.wait(100);
			}
		});
		
		pool.runTask(task);
		waitFor(100, ()->worker.activeTask() != null);
		synchronized(worker) { worker.notifyAll(); }
		waitFor(100, ()->worker.activeTask() == null);
	}
	
	@Test
	public void testSetsIdleNameWhenWaitingOnTask() {
		waitFor(100, ()->worker.thread().getName().contains("idle"));
	}
	
	@Test
	public void testSetsNameWithSourceReferenceWhenExecutingTask() {
		// The next two lines must be immediately adjacent, and in their current order!
		Task<?> task = new SimpleTask(taskset, "example", ()->Thread.sleep(1000));
		StackTraceElement here = (new Throwable()).getStackTrace()[0];
		
		String[] fileElements = here.getFileName().split("/");
		String srcRef = fileElements[fileElements.length - 1]
				      + ":"
				      + (here.getLineNumber() - 1);
		
		pool.runTask(task);
		waitFor(100, ()->worker.thread().getName().contains(srcRef));
	}
	
	@Test
	public void testSetsNameWithTaskSetNameWhenExecutingTask() {
		Task<?> task = new SimpleTask(taskset, "example", ()->{
			Thread.sleep(1000);
		});
		
		pool.runTask(task);
		waitFor(100, ()->worker.thread().getName().contains(taskset.name()));
	}
}
