package com.acrescrypto.shepherd.taskset;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeoutException;

import com.acrescrypto.shepherd.Callbacks.OpportunisticExceptionHandler;
import com.acrescrypto.shepherd.exceptions.TaskSetRequiresTagException;
import com.acrescrypto.shepherd.worker.Worker;
import com.acrescrypto.shepherd.worker.WorkerPool;

public abstract class TaskSet<T extends TaskSet<?>> {
	protected OpportunisticExceptionHandler exceptionHandler;
	protected WorkerPool                    pool;
	protected TaskSet<?>                    parent;
	protected String                        name;
	protected Map<Object,Object>            data        = new ConcurrentHashMap<>();
	protected Map<Object,Boolean>           tags        = new ConcurrentHashMap<>();
	protected Deque<Object>                 convenience = new ConcurrentLinkedDeque<>();
	protected boolean                       started,
	                                        cancelled,
	                                        finished;
	
	public TaskSet(String name) {
		this.name = name;
		this.pool = Worker.active() != null
				  ? Worker.active().pool()
				  : null;
	}
	
	public synchronized T await(long timeoutMs) throws InterruptedException, TimeoutException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		
		while(!isFinished() && System.currentTimeMillis() < deadline) {
			this.wait(timeoutMs);
		}
		
		if(!isFinished()) throw new TimeoutException();
		return self();
	}
	
	public WorkerPool pool() {
		if(pool == null && parent != null) return parent.pool();
		return pool;
	}
	
	public T pool(WorkerPool pool) {
		this.pool = pool;
		return self();
	}
	
	public TaskSet<?> parent() {
		return parent;
	}
	
	public T parent(TaskSet<?> parent) {
		this.parent = parent;
		return self();
	}
	
	public String name() {
		return name;
	}
	
	public T name(String name) {
		this.name = name;
		return self();
	}
	
	public T delegate() {
		TaskSet<?> active = Worker.active().activeTask().taskset();
		parent(active);
		
		return self();
	}
	
	public T sibling() {
		TaskSet<?> active = Worker.active().activeTask().taskset();
		parent(active.parent());
		return self();
	}
	
	public Object get(Object key) {
		return data.get(key);
	}
	
	public Integer getInteger(Object key) {
		return (Integer) data.get(key);
	}
	
	public Long getLong(Object key) {
		return (Long) data.get(key);
	}
	
	public String getString(Object key) {
		return (String) data.get(key);
	}
	
	public T set(Object key, Object value) {
		data.put(key, value);
		return self();
	}
	
	public T tag(Object tag) {
		tags.put(tag, true);
		return self();
	}
	
	public T untag(Object tag) {
		tags.put(tag, false);
		return self();
	}
	
	public boolean hasTag(Object tag) {
		if(tags.containsKey(tag)) return tags.get(tag);
		if(parent != null)        return parent.hasTag(tag);
		return false;
	}
	
	public T require(Object tag) throws TaskSetRequiresTagException {
		if(!hasTag(tag)) throw new TaskSetRequiresTagException(tag);
		return self();
	}
	
	public T exception(Throwable exception) {
		cancel();
		if(exceptionHandler != null) {
			try {
				exceptionHandler.handle(exception);
			} catch(Throwable exc2) {
				escalateException(exception);
				if(exc2 != exception && !exc2.getClass().equals(exception.getClass())) {
					exception(exc2);
				}
			}
		} else {
			escalateException(exception);
		}
		
		return self();
	}
	
	public T onException(OpportunisticExceptionHandler exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
		return self();
	}
	
	protected void escalateException(Throwable exc) {
		if(parent() == null) {
			pool.exception(exc);
			return;
		}
		
		parent().exception(exc);
	}
	
	public T cancel() {
		cancelled = finished = true;
		synchronized(this) { this.notifyAll(); }
		return self();
	}
	
	public T finish() {
		finished = true;
		synchronized(this) { this.notifyAll(); }
		return self();
	}
	
	public boolean isRunning() {
		return started && !isFinished();
	}
	
	public boolean isCancelled() {
		if(cancelled     ) return true;
		if(parent != null) return parent.isCancelled();
		return false;
	}
	
	public boolean isFinished() {
		if(finished      ) return true;
		if(parent != null) return parent.isFinished();
		return false;
	}
	
	public T push(Object item) {
		convenience.push(item);
		return self();
	}
	
	public Object pop() {
		return convenience.pop();
	}
	
	public T run() {
		if(isRunning()) return self();
		
		started = true;
		return execute();
	}
	
	protected abstract T execute();
	
	@SuppressWarnings("unchecked")
	protected T self() {
		return (T) this;
	}
}
