package com.acrescrypto.shepherd.worker;

import java.util.LinkedList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.acrescrypto.shepherd.Callbacks.OpportunisticExceptionHandler;
import com.acrescrypto.shepherd.core.Program;
import com.acrescrypto.shepherd.taskset.Task;

public class WorkerPool {
	public class WorkerLaidOffException extends Exception {
		private static final long serialVersionUID = 1L;
	}
	
	protected Program                                program;
	protected OpportunisticExceptionHandler          exceptionHandler;
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
			try {
				throw(exc);
			} catch(InterruptedException xx) {
				/* Thread was interrupted and no one caught this, so we're probably OK with
				 * whatever task we were handling being quietly ended. */
			} catch(Throwable xx) {
				throw(exc);
			}
		};
	}
	
	public Program program() {
		return program;
	}
	
	public String name() {
		return name;
	}
	
	/** Make a best effort to terminate all threads. */
	public synchronized WorkerPool shutdown() {
		workers(0);
		for(Worker worker : workers) {
			worker.thread().interrupt();
		}
		
		return this;
	}
	
	/** Shutdown and block until all threads terminate. 
	 * @throws TimeoutException Timeout expired before all threads terminated 
	 * @throws InterruptedException Thread was interrupted before worker threads could terminate */
	public WorkerPool shutdownAndWait(long timeoutMs) throws TimeoutException, InterruptedException  {
		long deadline = System.currentTimeMillis() + timeoutMs;
		shutdown();
		
		while(System.currentTimeMillis() < deadline
		   && threadGroup.activeCount() > 0) {
			Thread.sleep(1);
		}
		
		if(threadGroup.activeCount() > 0) {
			throw new TimeoutException();
		}
		
		return this;
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
	
	public WorkerPool onException(OpportunisticExceptionHandler handler) {
		this.exceptionHandler = handler;
		return this;
	}
	
	public WorkerPool exception(Throwable exc) {
		try {
			exceptionHandler.handle(exc);
		} catch (Throwable xx) {
			program.exception(xx);
		}
		
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
		if(workerCountVerified) return true;
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
