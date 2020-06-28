package com.acrescrypto.shepherd.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import com.acrescrypto.shepherd.Callbacks.ExceptionHandler;
import com.acrescrypto.shepherd.worker.WorkerPool;

public class Program {
	protected WorkerPool         workerPool;
	protected SignalHub          signalHub;
	protected ExceptionHandler   exceptionHandler;
	protected Map<String,Object> globals = new ConcurrentHashMap<>();
	
	public Program() {
	}
	
	public Program defaults() {
		int numWorkers  = Runtime.getRuntime().availableProcessors();
		this.workerPool = new WorkerPool(this).workers(numWorkers);
		this.signalHub  = new SignalHub(this);
		this.exceptionHandler = (exc) -> {};
		
		return this;
	}
	
	public Map<String,Object> globals() {
		return globals;
	}
	
	public WorkerPool pool() {
		return workerPool;
	}
	
	public Program pool(WorkerPool pool) {
		this.workerPool = pool;
		return this;
	}
	
	public SignalHub hub() {
		return signalHub;
	}
	
	public Program hub(SignalHub hub) {
		this.signalHub = hub;
		return this;
	}
	
	public Program stop() {
		if(workerPool != null) {
			workerPool.shutdown();
		}
		
		return this;
	}
	
	public Program stop(long timeoutMs) throws TimeoutException, InterruptedException {
		if(workerPool != null) {
			workerPool.shutdownAndWait(timeoutMs);
		}
		
		return this;
	}

	public Program exception(Throwable xx) {
		exceptionHandler.exception(xx);
		return this;
	}
	
	public Program onException(ExceptionHandler exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
		return this;
	}
	
	public ExceptionHandler onException() {
		return exceptionHandler;
	}
}
