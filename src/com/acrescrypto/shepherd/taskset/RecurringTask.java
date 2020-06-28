package com.acrescrypto.shepherd.taskset;

import com.acrescrypto.shepherd.Callbacks.TaskCallback;

public class RecurringTask extends Task<RecurringTask> {
	
	protected DeferredTaskSet             taskset;
	protected long                        periodMs;
	protected TaskCallback<RecurringTask> lambda;
	
	public RecurringTask(
			String name,
			DeferredTaskSet taskset,
			long periodMs,
			TaskCallback<RecurringTask> lambda)
	{
		super(name);
		this.lambda   = lambda;
		this.taskset  = taskset;
		
		period(periodMs);
		reset();
	}
	
	public RecurringTask reset() {
		this.notBefore = System.currentTimeMillis() + periodMs;
		return this;
	}
	
	public long period() {
		return periodMs;
	}
	
	public RecurringTask period(long periodMs) {
		this.periodMs = periodMs;
		return this;
	}

	@Override
	protected void execute() throws Exception {
		lambda.call(this);
		if(isCancelled()) return;
		
		reset();
		taskset.pool().addTask(this);
	}

	@Override
	public DeferredTaskSet taskset() {
		return (DeferredTaskSet) taskset;
	}

}
