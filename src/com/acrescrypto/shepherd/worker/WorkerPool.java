package com.acrescrypto.shepherd.worker;

import java.util.LinkedList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.acrescrypto.shepherd.Program;
import com.acrescrypto.shepherd.Callbacks.ExceptionHandler;
import com.acrescrypto.shepherd.taskset.Task;

public class WorkerPool {
	public class WorkerLaidOffException extends Exception {
		private static final long serialVersionUID = 1L;
	}
	
	protected Program                                program;
	protected ExceptionHandler                       exceptionHandler;
	protected int                                    targetWorkerCount;
	protected boolean                                workerCountVerified;
	protected String                                 name;
	protected LinkedList<Worker>                     workers = new LinkedList<>();
	protected PriorityBlockingQueue<Task<?>>         tasks   = new PriorityBlockingQueue<>();
	protected ThreadGroup                            threadGroup;
	
	public WorkerPool(Program program) {
		this.targetWorkerCount = 1;
		this.program           = program;
		this.threadGroup       = new ThreadGroup("WorkerPool");
		this.exceptionHandler  = (exc)->{
			// TODO: Default exception handler. Log as error?
			exc.printStackTrace();
		};
	}
	
	public String name() {
		return name;
	}
	
	public synchronized WorkerPool name(String name) {
		if(name.equals(this.name)) return this;
		
		this.name                = name;
		this.threadGroup         = new ThreadGroup("WorkerPool: " + name);
		for(Worker worker : workers) worker.threadGroupChanged();
		
		return this;
	}
	
	public ThreadGroup threadGroup() {
		return threadGroup;
	}
	
	public WorkerPool addTask(Task<?> task) {
		tasks.add(task);		
		return this;
	}
	
	public WorkerPool onException(ExceptionHandler handler) {
		this.exceptionHandler = handler;
		return this;
	}
	
	public WorkerPool exception(Throwable exc) {
		exceptionHandler.exception(exc);
		return this;
	}
	
	public int workers() {
		return targetWorkerCount;
	}
	
	public WorkerPool workers(int numWorkers) {
		targetWorkerCount = numWorkers;
		checkThreadCount();
		return this;
	}
	
	public WorkerPool run() {
		checkThreadCount();
		return this;
	}
	
	protected synchronized boolean checkThreadCount() {
		int delta = workers.size() - targetWorkerCount;
		if       (delta == 0) {
			workerCountVerified = true;
			return true;
		} else {
			workerCountVerified = false;
			
			if(delta <  0) {
				for(int i = 0; i < -delta; i++) {
					Worker newWorker = new Worker(this).run();
					workers.add(newWorker);
				}
				
				return true;
			} else {
				return false;
			}
		}
	}
	
	protected boolean isThisWorkerAllowedToContinue() {
		if( workerCountVerified) return true;
		return checkThreadCount();
	}
	
	protected Task<?> dequeueTask() throws InterruptedException {
		return tasks.poll(1, TimeUnit.MILLISECONDS);
	}
	
	protected synchronized void workerFinished(Worker worker) {
		workers.remove(worker);
		checkThreadCount();
	}
}
