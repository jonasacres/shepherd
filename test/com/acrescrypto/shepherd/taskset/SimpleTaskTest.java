package com.acrescrypto.shepherd.taskset;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.acrescrypto.shepherd.Program;
import com.acrescrypto.shepherd.exceptions.TaskFinishedException;
import com.acrescrypto.shepherd.taskset.SimpleTaskSet;
import com.acrescrypto.shepherd.worker.WorkerPool;

class SimpleTaskTest {
	class WorkerPoolStubber extends WorkerPool {
		Task<?> addedTask;

		public WorkerPoolStubber() {
			super(new Program());
		}
		
		public WorkerPoolStubber addTask(Task<?> task) {
			addedTask = task;
			return this;
		}
	}
	
	class SimpleTaskSetStubber extends SimpleTaskSet {
		boolean calledFinish, calledYield;
		Throwable exception;
		
		public SimpleTaskSetStubber() {
			super("stub");
			pool = new WorkerPoolStubber();
		}
		
		@Override
		protected SimpleTaskSet finishedTask(SimpleTask task) {
			calledFinish = true;
			return this;
		}
		
		@Override
		public SimpleTaskSet yield() {
			calledYield = true;
			return this;
		}
		
		@Override
		public SimpleTaskSet exception(Throwable exc) {
			this.exception = exc;
			return this;
		}
	}
	
	SimpleTaskSetStubber taskset;
	String               name;
	
	@BeforeEach
	public void beforeEach() {
		taskset = new SimpleTaskSetStubber();
		name    = "testcase";
	}
	
	
	@Test
	void testInvokesCallbackWhenRunAsAVoidCallback() {
		AtomicBoolean invoked = new AtomicBoolean(false);
		
		new SimpleTask(taskset, name, ()->invoked.set(true)).run();
		assertTrue(invoked.get());
	}
	
	@Test
	void testInvokesCallbackWhenRunAsASimpleTaskCallback() {
		AtomicBoolean invoked = new AtomicBoolean(false);
		
		new SimpleTask(taskset, name, (task)->{
			invoked.set(true);
			task.finish();
		}).run();
		
		assertTrue(invoked.get());
	}
	
	@Test
	void testThrowsTaskFinishedExceptionWhenFinishCalled() {
		assertThrows(TaskFinishedException.class, ()->{
			new SimpleTask(taskset, name, (task)->{}).finish();
		});
	}
	
	@Test
	void testNotifiesTaskSetWhenFinishCalled() {
		try { new SimpleTask(taskset, name, (task)->{}).finish(); }
		catch(TaskFinishedException exc) {}
		assertTrue(taskset.calledFinish);
	}
	
	@Test
	void testThrowsTaskFinishedExceptionWhenYieldCalled() {
		assertThrows(TaskFinishedException.class, ()->{
			new SimpleTask(taskset, name, (task)->{}).yield();
		});
	}
	
	@Test
	void testNotifiesTaskSetWhenYieldCalled() {
		try { new SimpleTask(taskset, name, (task)->{}).yield(); }
		catch(TaskFinishedException exc) {}
		assertTrue(taskset.calledYield);
	}
	
	@Test
	void testThrowsTaskFinishedExceptionWhenRepeatCalled() {
		assertThrows(TaskFinishedException.class, ()->{
			new SimpleTask(taskset, name, (task)->{}).repeat();
		});
	}
	
	@Test
	void testRequeuesTaskWhenRepeatCalled() {
		try { new SimpleTask(taskset, name, (task)->{}).repeat(); }
		catch(TaskFinishedException exc) {}
	}
	
	@Test
	void testIsFinishedReturnsFalseBeforeTaskRuns() {
		SimpleTask task = new SimpleTask(taskset, name, ()->{});
		assertFalse(task.isFinished());
	}
	
	@Test
	void testIsFinishedReturnsFalseWhenVoidCallbackReturns() {
		SimpleTask task = new SimpleTask(taskset, name, ()->{}).run();
		assertTrue(task.isFinished());
	}
	
	@Test
	void testIsFinishedReturnsIfTaskCompletesWithoutCallingFinishBeforeTaskRuns() {
		SimpleTask task = new SimpleTask(taskset, name, (tt)->{}).run();
		assertFalse(task.isFinished());
	}
	
	@Test
	void testIsFinishedReturnsTrueWhenFinishCalled() {
		SimpleTask task = new SimpleTask(taskset, name, (tt)->tt.finish()).run();
		assertTrue(task.isFinished());
	}
	
	@Test
	void testIsFinishedReturnsTrueWhenYieldCalled() {
		SimpleTask task = new SimpleTask(taskset, name, (tt)->tt.yield()).run();
		assertTrue(task.isFinished());
	}
	
	@Test
	void testIsFinishedReturnsFalseWhenRepeatCalled() {
		SimpleTask task = new SimpleTask(taskset, name, (tt)->tt.repeat()).run();
		assertFalse(task.isFinished());
	}
	
	@Test
	void testExceptionsAreProcessedByTaskSetExceptionHandler() {
		new SimpleTask(taskset, name, (tt)->{
			throw new RuntimeException();
		}).run();
		
		assertTrue(taskset.exception.getClass().equals(RuntimeException.class));
	}
}
