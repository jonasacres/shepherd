package com.acrescrypto.shepherd.taskset;

import com.acrescrypto.shepherd.Callbacks.TaskCallback;

public class DelayedTask extends Task<DelayedTask> {

	protected DeferredTaskSet             taskset;
	protected TaskCallback<DelayedTask>   lambda;

	public DelayedTask(
		String name,
		DeferredTaskSet taskset,
		long fireTime,
		TaskCallback<DelayedTask> lambda)
	{
		super(name);
		this.lambda   = lambda;
		this.taskset  = taskset;
		
		notBefore(fireTime);
	}
	
	@Override
	protected void execute() throws Exception {
		lambda.call(this);
	}

	@Override
	public TaskSet<?> taskset() {
		return taskset;
	}

}
