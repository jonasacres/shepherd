package com.acrescrypto.shepherd.taskset;

import com.acrescrypto.shepherd.exceptions.TaskFinishedException;

/** Describes a task to be performed. This class must be subclassed to be made useful. */
public abstract class Task<T extends Task<?>> implements Comparable<Task<?>> {
	protected int                 priority;
	protected long                notBefore;
	protected boolean             cancelled;
	protected String              name,
	                              sourceReference;
	protected StackTraceElement[] callstack;
	
	/** Execute the code for a Task.
	 * 
	 * @throws Exception Any exceptions thrown by execute() are handled by the owning TaskSet's exception handler.
	 * @throws TaskFinishedException An alternative to return. Does not signify error.
	 */
	protected abstract void execute() throws Exception;
	
	public abstract TaskSet<?> taskset();
	
	/** Initialize a new task with the given name. The task must still be added to a
	 * WorkerPool or otherwise assigned to a Worker. */
	public Task(String name) {
		name(name);
		callstack = (new Throwable()).getStackTrace();
	}
	
	/** Execute this task, passing any exceptions to the task's registered exception handler.
	 * If the task, or its owning TaskSet, is cancelled, then the task is not run.
	 * */
	public T run() {
		if(isCancelled()) return self();
		
		try {
			execute();
		} catch(TaskFinishedException exc) {
			// just another way to return from execute() -- ignore
		} catch(Throwable exc) {
			exception(exc);
		}
		
		return self();
	}
	
	/** Get the task's priority. The task with the biggest number runs first.
	 * */
	public int priority() {
		return priority;
	}
	
	/** Set the task's priority. The task with the biggest number runs first. */  
	public T priority(int priority) {
		this.priority = priority;
		return self();
	}
	
	/** Returns the minimum unix epoch millisecond timestamp at which this task
	 * is eligible to run.
	 * 
	 */
	public long notBefore() {
		return notBefore;
	}
	
	/** Sets the minimum unix epoch millisecond timestamp at which this task is
	 * eligible to run.
	 */
	public T notBefore(long notBefore) {
		this.notBefore = notBefore;
		return self();
	}
	
	/** Returns true if and only if this task is eligible to run according to its
	 * notBefore timestamp.
	 */
	public boolean ready() {
		return System.currentTimeMillis() >= notBefore;
	}
	
	/** Get the task's name. */
	public String name() {
		return name;
	}
	
	/** Set task's name. */
	public T name(String name) {
		this.name = name;
		return self();
	}
	
	/** Cancel the task. Cancelled tasks are removed from the queue without assignment to a
	 * Worker. Cancelled tasks that are already assigned to a Worker may continue to execute.
	 */
	public T cancel() {
		cancelled = true;
		return self();
	}
	
	/** True if task has been cancelled, or its owning TaskSet is finished. */
	public boolean isCancelled() {
		return cancelled || taskset().isFinished();
	}
	
	/** Process an exception using the handler for the owning TaskSet. */
	public T exception(Throwable exc) {
		taskset().exception(exc);
		return self();
	}
	
	@Override
	public int compareTo(Task<?> other) {
		int pdelta = other.priority - this.priority;
		if(pdelta < 0) return -1; // this task is higher priority
		if(pdelta > 0) return  1; // this task is lower priority
		
		return this.hashCode() - other.hashCode(); // use object hash as tiebreaker
	}
	
	public String sourceReference() {
		if(sourceReference == null) {
			sourceReference = calculateSourceReference();
		}
		
		return sourceReference;
	}
	
	@SuppressWarnings("unchecked")
	/** Return a reference to this object using the generic type, rather than the superclass
	 * type of Task.
	 */
	protected T self() {
		return (T) this;
	}
	
	/** Use the backtrace we took at Task creation to find where we created
	 * this Task in source
	 * @return A source reference, like "doTheThing file.java:1234"
	 */
	protected String calculateSourceReference() {
		try {
			for(StackTraceElement frame : callstack) {
				// skip through the stack to find the first thing NOT in Task or TaskSet
				Class<?> klass = Class.forName(frame.getClassName());
				if(Task   .class.isAssignableFrom(klass)) continue;
				if(TaskSet.class.isAssignableFrom(klass)) continue;
				
				// this frame is probably the creation point of the task.
				String[] components = frame.getFileName().split("/");
				return frame.getMethodName()
					 + " "
					 + components[components.length - 1]
					 + ":"
					 + frame.getLineNumber();
			}
		} catch(ClassNotFoundException exc) {}
		
		return "(unknown origin point)";
	}
}
