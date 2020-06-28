package com.acrescrypto.shepherd.taskset;

import java.util.concurrent.atomic.AtomicInteger;

import com.acrescrypto.shepherd.core.SignalHub;
import com.acrescrypto.shepherd.core.SignalHub.SignalCallback;
import com.acrescrypto.shepherd.exceptions.TaskFinishedException;

public class SignalTask extends Task<SignalTask> {
	protected String          signal;
	protected boolean         hasArgument;
	protected Object          argument;
	protected AtomicInteger   remainingInvocations;
	protected SignalCallback  lambda;
	protected TaskSet<?> taskset;
	
	public SignalTask(String name, TaskSet<?> taskset, String signal, SignalCallback lambda) {
		this(name, taskset, signal,null, false, lambda);
	}
	
	public SignalTask(String name, TaskSet<?> taskset, String signal, Object argument, SignalCallback lambda) {
		this(name, taskset, signal, argument, true, lambda);
	}
	
	public SignalTask(String name, TaskSet<?> taskset, String signal, Object argument, boolean hasArgument, SignalCallback lambda) {
		super(name);
		this.signal      = signal;
		this.taskset     = taskset;
		this.lambda      = lambda;
		this.argument    = argument;
		this.hasArgument = hasArgument;
	}
	
	public SignalTask times(int numTimes) {
		if(remainingInvocations == null) remainingInvocations = new AtomicInteger();
		remainingInvocations.set(numTimes);
		return this;
	}
	
	public int timesRemaining() {
		return remainingInvocations != null
		     ? remainingInvocations.get()
		     : -1;
	}
	
	public boolean hasArgument() {
		return hasArgument;
	}
	
	public Object argument() {
		return argument;
	}
	
	public String signal() {
		return signal;
	}

	@Override
	protected void execute() throws Exception {
		SignalHub hub = taskset().pool().program().hub();
		SignalCallback handlerCallback = (signal)->{
			if(taskset.isFinished()) {
				signal.registration().cancel();
				return;
			}
			
			if(remainingInvocations != null) {
				int count = remainingInvocations.decrementAndGet();
				if(count <= 0) {
					signal.registration().cancel();
					if(count < 0) return;
				}
			}
			
			try {
				lambda.call(signal);
			} catch(TaskFinishedException exc) {
				signal.registration().cancel();
			} catch(Throwable exc) {
				signal.registration().cancel();
				exception(exc);
			}
		};
		
		if(hasArgument) {
			hub.handle(signal, argument, handlerCallback);
		} else {
			hub.handle(signal,           handlerCallback);
		}
	}

	@Override
	public TaskSet<?> taskset() {
		return taskset;
	}
	
}
