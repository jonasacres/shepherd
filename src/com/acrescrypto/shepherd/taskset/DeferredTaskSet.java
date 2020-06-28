package com.acrescrypto.shepherd.taskset;

import com.acrescrypto.shepherd.Callbacks.TaskCallback;
import com.acrescrypto.shepherd.Callbacks.VoidCallback;
import com.acrescrypto.shepherd.core.SignalHub.SignalCallback;

public class DeferredTaskSet extends TaskSet<DeferredTaskSet> {
	public interface DeferredTaskSignalCallback {
		void call(String signal, Object argument) throws Throwable;
	}

	public DeferredTaskSet(String name) {
		super(name);
	}
	
	public DeferredTaskSet every(long periodMs, String name, TaskCallback<RecurringTask> callback) {
		pool.addTask(new RecurringTask(name, this, periodMs, callback));
		return this;
	}
	
	public DeferredTaskSet every(long periodMs, String name, VoidCallback callback) {
		return every(periodMs, name, (task)->callback.call());
	}
	
	public DeferredTaskSet at   (long timestampMs, String name, TaskCallback<DelayedTask> callback) {
		pool.addTask(new DelayedTask(name, this, timestampMs, callback));
		return this;
	}
	
	public DeferredTaskSet at   (long timestampMs, String name, VoidCallback callback) {
		return at(timestampMs, name, (task)->callback.call());
	}
	
	public DeferredTaskSet delay(long intervalMs, String name, TaskCallback<DelayedTask> callback) {
		return at(System.currentTimeMillis() + intervalMs, name, callback);
	}
	
	public DeferredTaskSet delay(long intervalMs, String name, VoidCallback callback) {
		return delay(intervalMs, name, (task)->callback.call());
	}
	
	public DeferredTaskSet on   (String signal, String name, SignalCallback callback) {
		new SignalTask(name, this, signal, callback).run();
		return this;
	}
	
	public DeferredTaskSet on   (String signal, String name, VoidCallback callback) {
		return on(signal, name, (task)->callback.call());
	}
	
	public DeferredTaskSet on   (String signal, Object arg, String name, SignalCallback callback) {
		new SignalTask(name, this, signal, arg, callback).run();
		return this;
	}
	
	public DeferredTaskSet on   (String signal, Object arg, String name, VoidCallback callback) {
		return on(signal, arg, name, (task)->callback.call());
	}

	@Override
	public DeferredTaskSet run() {
		/* DeferredTaskSet is unusual in that it does not need to invoke run() for its
		 * children to operate.
		 */
		return this;
	}
}
