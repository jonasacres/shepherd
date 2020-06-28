package com.acrescrypto.shepherd.taskset;

import java.util.concurrent.atomic.AtomicInteger;

import com.acrescrypto.shepherd.core.SignalHub;
import com.acrescrypto.shepherd.core.SignalHub.SignalCallback;

public class SignalTask extends Task<SignalTask> {
	protected String          signal;
	protected boolean         hasArgument;
	protected Object          argument;
	protected AtomicInteger   remainingInvocations;
	protected SignalCallback  lambda;
	protected DeferredTaskSet taskset;
	
	public SignalTask(String name, DeferredTaskSet taskset, String signal, SignalCallback lambda) {
		super(name);
		this.signal      = signal;
		this.taskset     = taskset;
		this.lambda      = lambda;
		this.hasArgument = false;
	}
	
	public SignalTask(String name, DeferredTaskSet taskset, String signal, Object argument, SignalCallback lambda) {
		super(name);
		this.signal      = signal;
		this.taskset     = taskset;
		this.lambda      = lambda;
		this.argument    = argument;
		this.hasArgument = true;
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
			
			lambda.call(signal);
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
