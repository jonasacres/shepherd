package com.acrescrypto.shepherd.taskset;

import com.acrescrypto.shepherd.Callbacks.TaskCallback;
import com.acrescrypto.shepherd.Callbacks.VoidCallback;
import com.acrescrypto.shepherd.exceptions.TaskFinishedException;

/** Describes a task that accepts no arguments and produces no return value, and runs
 * as part of a SimpleTaskSet. */
public class SimpleTask extends Task<SimpleTask> {
	protected TaskCallback<SimpleTask> lambda;
	protected SimpleTaskSet            taskset;
	protected boolean                  important;
	protected boolean                  finished;
	protected boolean                  after;
	
	/** Construct a SimpleTask from a SimpleTaskCallback. This callback received a reference
	 * to the new SimpleTask itself. The lambda is expected to asynchronously indicate
	 * task completion via the .finish() or .yield() method.
	 *  
	 * @param taskset
	 * @param name
	 * @param lambda
	 */
	public SimpleTask(SimpleTaskSet taskset, String name, TaskCallback<SimpleTask> lambda) {
		super(name);
		this.taskset = taskset;
		this.lambda = lambda;
	}
	
	/** Construct a SimpleTask from a void callback. This task will be marked finished when
	 * the lambda returns.
	 * 
	 * @param taskset Parent SimpleTaskSet owning this SimpleTask
	 * @param name Name of this SimpleTask, for accounting purposes
	 * @param lambda Lambda function to be invoked when this task runs
	 */
	public SimpleTask(SimpleTaskSet taskset, String name, VoidCallback lambda) {
		super(name);
		this.taskset = taskset;
		this.lambda = (task)->{
			lambda.call();
			task.finish();
		};
	}
	
	@Override
	protected void execute() throws Exception {
		lambda.call(this);
	}

	@Override
	public SimpleTaskSet taskset() {
		return taskset;
	}
	
	/** True when this task has completed execution of its lambda. */
	public boolean isFinished() {
		return finished;
	}
	
	/** True when this task is marked important */
	public boolean isImportant() {
		return important;
	}
	
	/** Mark this as an 'after' task that runs after the SimpleTaskSet is finished. */
	public SimpleTask after() {
		after = true;
		return this;
	}
	
	/** True when this is an 'after' task that runs after the SimpleTaskSet is finished. */
	public boolean isAfter() {
		return after;
	}
	
	/** Causes execution of this task to cease, and notifies the SimpleTaskSet that
	 * it has finished.
	 */
	public void finish() {
		this.finished = true;
		taskset.finishedTask(this);
		throw new TaskFinishedException();
	}
	
	/** Causes execution of this task to cease, and ceases all further processing in
	 * the SimpleTaskSet that owns this task. Any `after` handlers registered for the
	 * SimpleTaskSet will be invoked, but additional tasks scheduled via `task` or
	 * `then` will not be scheduled onto Workers if they have not been scheduled already.
	 * Parallel Tasks already scheduled onto Workers will not be interrupted.
	 */
	public SimpleTask yield() {
		this.finished = true;
		taskset.yield();
		throw new TaskFinishedException();
	}
	
	/** Causes execution of this task to cease, and it is added back to the task queue
	 * to be executed by a Worker again.
	 */
	public SimpleTask repeat() {
		taskset().pool().addTask(this);
		throw new TaskFinishedException();
	}
	
	/** Mark this task as important. Non-important tasks can only run when all parallel
	 * important tasks have invoked their .bootstrapped method.
	 */
	public SimpleTask important() {
		this.important = true;
		return this;
	}
	
	/** Mark this task as having completed its bootstrap phase. This has no effect unless
	 * the task is marked as important.
	 */
	public SimpleTask registered() {
		if(isImportant()) {
			taskset().registered(this);
		}
		
		return this;
	}
	
	@Override
	public boolean isCancelled() {
		if(after) {
			return cancelled || taskset.isCancelled();
		} else {
			return cancelled || taskset.isFinished();
		}
	}
	
	@Override
	public int compareTo(Task<?> other) {
		if(!(other instanceof SimpleTask)) {
			return super.compareTo(other);
		}
		
		SimpleTask oo = (SimpleTask) other;
		if(isImportant() == oo.isImportant()) {
			return super.compareTo(oo);
		}
		
		return isImportant()
			 ? -1
			 :  1;
	}
}
