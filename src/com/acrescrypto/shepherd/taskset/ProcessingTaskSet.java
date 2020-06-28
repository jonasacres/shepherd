package com.acrescrypto.shepherd.taskset;

import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import com.acrescrypto.shepherd.Callbacks.VoidCallback;

public class ProcessingTaskSet<A,R> extends TaskSet<ProcessingTaskSet<A,R>> {
	public interface ProcessingTaskSimpleCallback<A,R> {
		R call(A argument) throws Exception;
	}
	
	public interface ProcessingTaskCallback<A,R> {
		void call(ProcessingTask<A,R> task, A argument) throws Exception;
	}
	
	public interface ProcessingTaskSetEachCallback<A,R> {
		void call(A argument, R result) throws Exception;
	}
	
	public interface ProcessingTaskSetListCallback<A,R> {
		void call(Deque<R> results) throws Exception;
	}
	
	public interface ProcessingTaskSetMapCallback<A,R> {
		void call(Map<A,R> results) throws Exception;
	}
	
	protected ConcurrentLinkedDeque<ProcessingTask<A,R>> arguments      = new ConcurrentLinkedDeque<>();
	protected ProcessingTaskCallback<A,R>                lambda;
	protected AtomicInteger                              numOutstanding = new AtomicInteger();
	protected SimpleTaskSet                              afterTaskSet;
	
	public ProcessingTaskSet(String name) {
		super(name);
		afterTaskSet = new SimpleTaskSet("after").parent(this);
	}
	
	public ProcessingTaskCallback<A,R> lambda() {
		return lambda;
	}
	
	public Deque<ProcessingTask<A,R>> arguments() {
		return arguments;
	}
	
	public ProcessingTaskSet<A,R> lambda(ProcessingTaskCallback<A,R> lambda) {
		this.lambda = lambda;
		return this;
	}
	
	public ProcessingTaskSet<A,R> lambda(ProcessingTaskSimpleCallback<A,R> lambda) {
		this.lambda = (task, arg)->task.finish(lambda.call(arg));
		return this;
	}

	@Override
	public ProcessingTaskSet<A,R> execute() {
		if(lambda == null) throw new RuntimeException("ProcessingTaskSet " + name + " run without registered lambda");
		
		if(arguments.isEmpty()) {
			runAfters();
			return this;
		}
		
		for(ProcessingTask<A,R> task : arguments) {
			pool().addTask(task);
		}
		
		return this;
	}
	
	public ProcessingTaskSet<A,R> add(A argument) {
		ProcessingTask<A,R> task = new ProcessingTask<>(this, argument);
		numOutstanding.incrementAndGet();
		arguments.add(task);
		if(isRunning()) pool().addTask(task);
		return this;
	}
	
	public ProcessingTaskSet<A,R> add(Collection<A> arguments) {
		for(A argument : arguments) {
			add(argument);
		}
		
		return this;
	}
	
	public ProcessingTaskSet<A,R> after(VoidCallback callback) {
		afterTaskSet.task(callback);
		return this;
	}
	
	public ProcessingTaskSet<A,R> each(ProcessingTaskSetEachCallback<A,R> callback) {
		return after(()->{
			for(ProcessingTask<A,R> argument : arguments) {
				callback.call(argument.argument(), argument.result());
			}
		});
	}
	
	public ProcessingTaskSet<A,R> list(ProcessingTaskSetListCallback<A,R> callback) {
		return after(()->{
			LinkedList<R> results = new LinkedList<>();
			for(ProcessingTask<A,R> argument : arguments) {
				results.add(argument.result());
			}
			
			callback.call(results);
		});
	}
	
	public ProcessingTaskSet<A,R> map(ProcessingTaskSetMapCallback<A,R> callback) {
		return after(()->{
			HashMap<A,R> results = new HashMap<>();
			for(ProcessingTask<A,R> argument : arguments) {
				results.put(argument.argument(), argument.result());
			}
			
			callback.call(results);
		});
	}
	
	protected void finishedTask(ProcessingTask<A,R> task) {
		int remaining = numOutstanding.decrementAndGet();
		if(remaining != 0) return;
		runAfters();
	}
	
	protected void runAfters() {
		afterTaskSet
			.after("mark finished", ()->this.finish())
			.run();
	}
}
