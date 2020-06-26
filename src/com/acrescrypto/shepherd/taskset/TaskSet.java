package com.acrescrypto.shepherd.taskset;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

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
	protected boolean                       cancelled,
	                                        paused;
	
	public TaskSet(String name) {
		this.name = name;
		this.pool = Worker.active() != null
				  ? Worker.active().pool()
				  : null;
	}
	
	public WorkerPool pool() {
		return pool;
	}
	
	public T pool(WorkerPool pool) {
		this.pool = pool;
		return self();
	}
	
	public TaskSet<?> parent() {
		return parent;
	}
	
	public TaskSet<?> parent(TaskSet<?> parent) {
		this.parent = parent;
		return this;
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
	
	protected void escalateException(Throwable exc) {
		if(parent() == null) {
			pool.exception(exc);
			return;
		}
		
		parent().exception(exc);
	}
	
	public T cancel() {
		cancelled = true;
		pause();
		return self();
	}
	
	public boolean cancelled() {
		return cancelled;
	}
	
	public T pause() {
		paused = true;
		return self();
	}
	
	public T unpause() {
		paused = false;
		// TODO: resume scheduling somehow 
		return self();
	}
	
	public T setPaused(boolean paused) {
		if(paused) {
			pause();
		} else {
			unpause();
		}
		
		return self();
	}
	
	public boolean paused() {
		return paused;
	}
	
	public T push(Object item) {
		convenience.push(item);
		return self();
	}
	
	public Object pop() {
		return convenience.pop();
	}
	
	public abstract T run();
	
	@SuppressWarnings("unchecked")
	protected T self() {
		return (T) this;
	}
}
