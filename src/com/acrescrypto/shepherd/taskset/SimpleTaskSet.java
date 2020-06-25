package com.acrescrypto.shepherd.taskset;

import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.acrescrypto.shepherd.Callbacks.VoidCallback;
import com.acrescrypto.shepherd.taskset.SimpleTask.SimpleTaskCallback;
import com.acrescrypto.shepherd.worker.WorkerPool;

/** Describes a set of simple tasks, with no return value or argument. These tasks may
 * run in parallel (.task), or be gated to run only after all previous tasks have been
 * completed (.then). 
 */
public class SimpleTaskSet extends TaskSet<SimpleTaskSet> {
	protected Deque<Deque<SimpleTask>> tasks  = new ConcurrentLinkedDeque<>();
	protected Deque<SimpleTask>        after  = new ConcurrentLinkedDeque<>();
	protected boolean                  finished;
	
	public SimpleTaskSet(String name) {
		super(name);
	}
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in this SimpleTaskSet. */
	public SimpleTaskSet task(SimpleTaskCallback lambda) {
		return task(new SimpleTask(this, "SimpleTask", lambda));
	}
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in this SimpleTaskSet. */
	public SimpleTaskSet task(VoidCallback lambda) {
		return task(new SimpleTask(this, "SimpleTask", lambda));
	}

	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in this SimpleTaskSet. */
	public SimpleTaskSet task(String name, SimpleTaskCallback lambda) {
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
		if(tasks.isEmpty()) addGate();
		tasks.getLast().add(task);
		return this;
	}
	
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in subsequent calls to this SimpleTaskSet, but will not run until all previously-defined
	 * tasks have completed. */
	public SimpleTaskSet then(SimpleTaskCallback lambda) {
		addGate();
		return task(lambda);
	}
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in subsequent calls to this SimpleTaskSet, but will not run until all previously-defined
	 * tasks have completed. */
	public SimpleTaskSet then(VoidCallback lambda) {
		addGate();
		return task(lambda);
	}

	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in subsequent calls to this SimpleTaskSet, but will not run until all previously-defined
	 * tasks have completed. */
	public SimpleTaskSet then(String name, SimpleTaskCallback lambda) {
		addGate();
		return task(name, lambda);
	}
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in subsequent calls to this SimpleTaskSet, but will not run until all previously-defined
	 * tasks have completed. */
	public SimpleTaskSet then(String name, VoidCallback lambda) {
		addGate();
		return task(name, lambda);
	}
	
	/** Perform a task with no arguments. May run in parallel with other tasks defined
	 * in subsequent calls to this SimpleTaskSet, but will not run until all previously-defined
	 * tasks have completed. */
	public SimpleTaskSet then(SimpleTask task) {
		addGate();
		return task(task);
	}
	
	/** Perform a task after all tasks scheduled with .task() or .then() are completed, or when
	 * .yield is invoked on a task. Aftertasks are expected to invoke .finish() when
	 * processing is complete. Behavior is undefined when aftertasks invoke .yield().
	 * 
	 * @param lambda Lambda to be invoked for this aftertask
	 */
	public SimpleTaskSet after(SimpleTaskCallback lambda) {
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
	public SimpleTaskSet after(String name, SimpleTaskCallback lambda) {
		return after(name, (task)->lambda.call(task));
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
		after.add(task);
		return this;
	}
	
	@Override
	public SimpleTaskSet run() {
		enqueueCurrentGroup();
		return this;
	}
	
	/** Immediately stop processing all further tasks. */
	public SimpleTaskSet yield() {
		cancel();
		return this;
	}
	
	@Override
	public SimpleTaskSet cancel() {
		super.cancel();
		finished = true;
		return this;
	}
	
	public WorkerPool pool() {
		return pool;
	}
	
	public boolean isFinished() {
		return finished;
	}

	/** "Gate" all new tasks; all tasks added after calling addGate() will run
	 * only after all tasks added prior to this addGate() call have completed. */
	protected void addGate() {
		tasks.add(new ConcurrentLinkedDeque<>());
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
		if(finished)                 return;
		if(cancelled())              return;
		if(!isCurrentGateComplete()) return;
		
		synchronized(this) {
			if(!isCurrentGateComplete()) return;
			try { tasks.pop(); } catch(NoSuchElementException exc) {}
			enqueueCurrentGroup();
		}
	}
	
	/** Add all queued tasks up to the next gate. */ 
	protected synchronized void enqueueCurrentGroup() {
		if(tasks.isEmpty()) {
			enqueueAfterTasks();
		} else {
			PriorityQueue<SimpleTask> byPriority = new PriorityQueue<SimpleTask>(tasks.getFirst());
			for(SimpleTask task : byPriority) {
				pool.addTask(task);
			}
		}
	}
	
	/** Add all aftertasks to task queue, and mark this set as finished. */
	protected synchronized void enqueueAfterTasks() {
		finished = true;
		
		for(SimpleTask task : after) {
			pool.addTask(task);
		}
	}
}
