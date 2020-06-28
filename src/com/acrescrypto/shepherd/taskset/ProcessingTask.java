package com.acrescrypto.shepherd.taskset;

import com.acrescrypto.shepherd.exceptions.TaskFinishedException;

public class ProcessingTask<A,R> extends Task<ProcessingTask<A,R>> {

	protected ProcessingTaskSet<A,R>      taskset;
	protected A                           argument;
	protected R                           result;
	protected boolean                     finished;
	
	public ProcessingTask(ProcessingTaskSet<A,R> taskset, A argument) {
		super(
				(argument == null
					? "(null)"
				    : argument.toString())
				+ " ("
				+ taskset.name() + ")");
		this.taskset  = taskset;
		this.argument = argument;
	}
	
	public ProcessingTask<A,R> argument(A argument) {
		this.argument = argument;
		return this;
	}
	
	public A argument() {
		return argument;
	}
	
	public R result() {
		return result;
	}
	
	public boolean isFinished() {
		return finished;
	}
	
	public void finish(R result) {
		this.finished = true;
		this.result   = result;
		taskset.finishedTask(this);
		throw new TaskFinishedException();
	}

	@Override
	protected void execute() throws Exception {
		taskset.lambda().call(this, argument);
	}

	@Override
	public ProcessingTaskSet<A,R> taskset() {
		return taskset;
	}

}
