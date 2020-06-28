package com.acrescrypto.shepherd.taskset;

import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import com.acrescrypto.shepherd.Callbacks.TaskCallback;
import com.acrescrypto.shepherd.Callbacks.VoidCallback;
import com.acrescrypto.shepherd.core.SignalHub.SignalRegistration.SignalMessage;

/** Describes a set of simple tasks, with no return value or argument. These tasks may
 * run in parallel (.task), or be gated to run only after all previous tasks have been
 * completed (.then). 
 */
public class SimpleTaskSet extends TaskSet<SimpleTaskSet> {
	public interface SimpleTaskSignalFullCallback {
		void call(SignalMessage sigmsg, SimpleTask task) throws Throwable;
	}
	
	public interface SimpleTaskSignalVoidCallback {
		void call() throws Throwable;
	}
	
	protected Deque<Deque<SimpleTask>> allTasks  = new ConcurrentLinkedDeque<>();
	protected Deque<Deque<SimpleTask>> tasks     = new ConcurrentLinkedDeque<>();
	protected Deque<SimpleTask>        after     = new ConcurrentLinkedDeque<>();
	protected AtomicInteger            pendingRegistrations;
	
	public SimpleTaskSet(String name) {
		super(name);
	}
	
	/** List all tasks performed in this taskset, including finished ones.
	 * @return A list of lists. The outer list represents task groups, and contains lists of
	 * tasks that may run in parallel. Each task within a given list must complete before the
	 * next list of tasks may begin.
	 */
	public Deque<Deque<SimpleTask>> tasks() {
		return allTasks;
	}
	
	/** List tasks scheduled to be performed in this task set, not including finished tasks,
	 * or tasks that are presently scheduled onto a Worker.
	 * @return A list of lists. The outer list represents task groups, and contains lists of
	 * tasks that may run in parallel. Each task within a given list must complete before the
	 * next list of tasks may begin.
	 */
	public Deque<Deque<SimpleTask>> pendingTasks() {
		return tasks;
	}
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in this SimpleTaskSet. */
	public SimpleTaskSet task(TaskCallback<SimpleTask> lambda) {
		return task(new SimpleTask(this, "SimpleTask", lambda));
	}
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in this SimpleTaskSet. */
	public SimpleTaskSet task(VoidCallback lambda) {
		return task(new SimpleTask(this, "SimpleTask", lambda));
	}

	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in this SimpleTaskSet. */
	public SimpleTaskSet task(String name, TaskCallback<SimpleTask> lambda) {
		return task(new SimpleTask(this, name, lambda));
	}
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in this SimpleTaskSet. */
	public SimpleTaskSet task(String name, VoidCallback lambda) {
		return task(new SimpleTask(this, name, lambda));
	}
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in this SimpleTaskSet. */
	public SimpleTaskSet task(SimpleTask task) {
		if(tasks.isEmpty()) gate();
		tasks   .getLast().add(task);
		allTasks.getLast().add(task);
		return this;
	}
	
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in subsequent calls to this SimpleTaskSet, but will not run until all previously-defined
	 * tasks have completed. */
	public SimpleTaskSet then(TaskCallback<SimpleTask> lambda) {
		gate();
		return task(lambda);
	}
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in subsequent calls to this SimpleTaskSet, but will not run until all previously-defined
	 * tasks have completed. */
	public SimpleTaskSet then(VoidCallback lambda) {
		gate();
		return task(lambda);
	}

	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in subsequent calls to this SimpleTaskSet, but will not run until all previously-defined
	 * tasks have completed. */
	public SimpleTaskSet then(String name, TaskCallback<SimpleTask> lambda) {
		gate();
		return task(name, lambda);
	}
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in subsequent calls to this SimpleTaskSet, but will not run until all previously-defined
	 * tasks have completed. */
	public SimpleTaskSet then(String name, VoidCallback lambda) {
		gate();
		return task(name, lambda);
	}
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in subsequent calls to this SimpleTaskSet, but will not run until all previously-defined
	 * tasks have completed. */
	public SimpleTaskSet then(SimpleTask task) {
		gate();
		return task(task);
	}
	
	/** Perform a task after a signal is received. The callback is invoked with a the signal
	 * argument, and a reference to the SimpleTask object created by this method. This reference
	 * must be marked completed (e.g. using .finish()).
	 */
	public SimpleTaskSet waitForSignal(String signal, String name, SimpleTaskSignalFullCallback callback) {
		return waitForSignalActual(signal, null, false, name, callback);
	}
	
	public SimpleTaskSet waitForSignal(String signal, SimpleTaskSignalFullCallback callback) {
		return waitForSignal(signal, "waitForSignal " + signal, callback);
	}
	
	/** Perform a task after a signal is received whose argument matches the specified object
	 * using the .equals method. The callback is invoked with a the signal argument, and a reference to the SimpleTask object created by this method. This reference
	 * must be marked completed (e.g. using .finish()).
	 */
	public SimpleTaskSet waitForSignal(String signal, Object argument, String name, SimpleTaskSignalFullCallback callback) {
		return waitForSignalActual(signal, argument, true, name, callback);
	}
	
	protected SimpleTaskSet waitForSignalActual(String signal, Object argument, boolean hasArgument, String name, SimpleTaskSignalFullCallback callback) {
		return task(new SimpleTask(
				this,
				name + " (setup)",
				(task)->{
					new SignalTask(name, this, signal, argument, hasArgument, (sigmsg)->{
						callback.call(sigmsg, task);
					}).times(1)
					  .run();
					task.registered();
				}
			).important()
		);
	}
	
	public SimpleTaskSet waitForSignal(String signal, Object argument, SimpleTaskSignalFullCallback callback) {
		return waitForSignal(signal, argument, "waitForSignal " + signal, callback);
	}
	
	/** Perform a task after a signal is received. The task is considered finished when the
	 * callback returns. The callback receives no arguments.
	 */
	public SimpleTaskSet waitForSignal(String signal, String name, SimpleTaskSignalVoidCallback callback) {
		return waitForSignal(signal, name, (sigmsg, task)->{
			callback.call();
			task.finish();
		});
	}
	
	public SimpleTaskSet waitForSignal(String signal, SimpleTaskSignalVoidCallback callback) {
		return waitForSignal(signal, "waitForSignal " + signal, (sigmsg, task)->{
			callback.call();
			task.finish();
		});
	}
	
	/** List tasks to be performed after all other tasks have completed, as registered with
	 * .after(...).
	 */
	public Deque<SimpleTask> after() {
		return after;
	}
	
	/** Perform a task after all tasks scheduled with .task() or .then() are completed, or when
	 * .yield is invoked on a task. Aftertasks are expected to invoke .finish() when
	 * processing is complete. Behavior is undefined when aftertasks invoke .yield().
	 * 
	 * @param lambda Lambda to be invoked for this aftertask
	 */
	public SimpleTaskSet after(TaskCallback<SimpleTask> lambda) {
		return after("after", lambda);
	}
	
	/** Perform a task after all tasks scheduled with .task() or .then() are completed, or when
	 * .yield is invoked on a task. Aftertasks are expected to invoke .finish() when
	 * processing is complete. Behavior is undefined when aftertasks invoke .yield().
	 * 
	 * @param lambda Lambda to be invoked for this aftertask
	 */
	public SimpleTaskSet after(VoidCallback lambda) {
		return after("after", lambda);
	}
	
	/** Perform a task after all tasks scheduled with .task() or .then() are completed, or when
	 * .yield is invoked on a task. Aftertasks are expected to invoke .finish() when
	 * processing is complete. Behavior is undefined when aftertasks invoke .yield().
	 * 
	 * @param name Name of this aftertask, for accounting purposes
	 * @param lambda Lambda to be invoked for this aftertask
	 */
	public SimpleTaskSet after(String name, TaskCallback<SimpleTask> lambda) {
		return after(new SimpleTask(this, name, lambda));
	}
	
	/** Perform a task after all tasks scheduled with .task or .then are completed, or when
	 * .yield is invoked on a task.
	 * 
	 * @param name Name of this aftertask, for accounting purposes
	 * @param lambda Lambda to be invoked for this aftertask
	 */
	public SimpleTaskSet after(String name, VoidCallback lambda) {
		return after(name, (task)->{
			lambda.call();
			task.finish();
		});
	}
	
	/** Perform a task after all tasks scheduled with .task or .then are completed, or when
	 * .yield is invoked on a task.
	 * 
	 * @param task Task to be performed after completion of this SimpleTaskSet
	 */
	public SimpleTaskSet after(SimpleTask task) {
		after.add(task.after());
		return this;
	}
	
	/** Begin executing tasks from this SimpleTaskSet. The behavior of calling .run multiple
	 * times is not defined.
	 */
	@Override
	public SimpleTaskSet execute() {
		enqueueNextTier();
		return this;
	}
	
	/** Immediately stop processing all further tasks. */
	public SimpleTaskSet yield() {
		if(finished) return this;
		enqueueAfterTasks();
		return this;
	}
	
	/** Mark a task as registered. Tasks not marked important can only run once all
	 * important tasks in the same task group have called registered().
	 */
	public SimpleTaskSet registered(SimpleTask task) {
		if(pendingRegistrations.decrementAndGet() == 0) {
			enqueueTasksByImportance(nextGroup(), false);
		}
		
		return this;
	}

	/** "Gate" all new tasks; all tasks added after calling addGate() will run
	 * only after all tasks added prior to this addGate() call have completed. */
	public SimpleTaskSet gate() {
		tasks.add(new ConcurrentLinkedDeque<>());
		allTasks.add(new ConcurrentLinkedDeque<>());
		return this;
	}
	
	/** A SimpleTask has completed its lambda. */
	protected SimpleTaskSet finishedTask(SimpleTask task) {
		checkQueue();
		return this;
	}
	
	/** Check to see if we've finished all the needed tasks to clear the next gate and
	 * unlock the next set of tasks in this SimpleTaskSet, if any.
	 */
	protected boolean isCurrentGateComplete() {
		if(tasks.isEmpty()) return true;
		
		for(SimpleTask task : tasks.getFirst()) {
			if(!task.isFinished()) return false;
		}
		
		return true;
	}
	
	/** Check to see if we need to move past the next gate in the queue, and if so,
	 * schedule the next batch of tasks.
	 */
	protected void checkQueue() {
		if(isFinished())                 return;
		if(!isCurrentGateComplete())     return;
		
		synchronized(this) {
			if(!isCurrentGateComplete()) return;
			try { tasks.pop(); } catch(NoSuchElementException exc) {}
			enqueueNextTier();
		}
	}
	
	/** Add all queued tasks up to the next gate. */ 
	protected synchronized void enqueueNextTier() {
		PriorityQueue<SimpleTask> currentGroup = nextGroup();
		
		if(currentGroup == null) {
			enqueueAfterTasks();
			return;
		}
		
		int numImportant = 0;
		for(SimpleTask task : currentGroup) {
			if(task.isImportant()) numImportant++;
		}
		
		pendingRegistrations = new AtomicInteger(numImportant);
		enqueueTasksByImportance(currentGroup, numImportant != 0);
	}
	
	protected synchronized void enqueueTasksByImportance(PriorityQueue<SimpleTask> currentGroup, boolean importance) {
		if(currentGroup == null) return;
		for(SimpleTask task : currentGroup) {
			if(task.isImportant() != importance) continue;
			pool().addTask(task);
		}
	}
	
	/** Get the next list of tasks to be performed. */
	protected synchronized PriorityQueue<SimpleTask> nextGroup() {
		Deque<SimpleTask> rawGroup = tasks.peek();
		
		while(rawGroup != null && rawGroup.isEmpty()) {
			tasks.pop();
			rawGroup = tasks.peek();
		}
		
		if(rawGroup == null) return null;
		
		return new PriorityQueue<>(rawGroup);
	}
	
	/** Add all aftertasks to task queue, and mark this set as finished. */
	protected synchronized void enqueueAfterTasks() {
		if(finished) return;
		finished = true;
		
		for(SimpleTask task : after) {
			pool().addTask(task);
		}
	}
}
