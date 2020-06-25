package com.acrescrypto.shepherd.worker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.acrescrypto.shepherd.taskset.Task;

public class Worker {
	protected static Map<Thread,Worker> WorkersByThread = new ConcurrentHashMap<>();
	
	public static Worker active() {
		return WorkersByThread.get(Thread.currentThread());
	}
	
	protected WorkerPool pool;
	protected Task<?>    activeTask;
	protected Thread     thread;
	protected boolean    threadGroupChanged;
	
	public Worker(WorkerPool pool) {
		this.pool = pool;
	}
	
	public Worker run() {
		if(thread != null) throw new RuntimeException("Worker was run() multiple times");
		thread = new Thread(
				pool.threadGroup(),
				()->{
					try {
						runloop();
					} catch(Throwable exc) {
						System.out.println("Check this shit out");
						exc.printStackTrace();
					}
				},
				"Worker (new)");
		thread.start();
		return this;
	}
	
	public WorkerPool pool() {
		return pool;
	}
	
	public Thread thread() {
		return thread;
	}
	
	public Task<?> activeTask() {
		return activeTask;
	}
	
	protected void threadGroupChanged() {
		threadGroupChanged = true;
	}
	
	protected void runloop() {
		WorkersByThread.put(Thread.currentThread(), this);
		
		try {
			while(!threadGroupChanged
			   &&  pool.isThisWorkerAllowedToContinue())
			{
				try {
					Task<?> newTask = pool.dequeueTask();
					if(newTask != null) {
						beginTask(newTask);
					}
				} catch(InterruptedException exc) {
					// Someone told our thread to wrap it up, so let's oblige
					break;
				} catch(Throwable exc) {
					exc.printStackTrace();
					pool.exception(exc);
				} finally {
					thread.setName("Worker (idle)");
				}
			}
		} finally {
			thread.setName("Worker (cancelled)");
			pool.workerFinished(this);
		}
	}
	
	protected void beginTask(Task<?> task) {
		thread.setName(task.sourceReference() + " " + task.taskset().name());
		this.activeTask = task;
		try {
			task.run();
		} finally {
			this.activeTask = null;
		}
	}
}
