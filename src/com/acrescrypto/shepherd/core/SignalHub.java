package com.acrescrypto.shepherd.core;

import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.acrescrypto.shepherd.core.SignalHub.SignalRegistration.SignalMessage;
import com.acrescrypto.shepherd.exceptions.SignalRegistrationCancelledException;

public class SignalHub {
	public interface SignalCallback {
		/** A callback accepting an argument (possibly null) provided during signal generation.
		 * @param argument Arbitrary data specified by signal generator.
		 * @throws SignalRegistrationCancelledException Indicates that this callback should no longer be invoked when this signal is generated in the future.
		 */
		void call(SignalMessage signal) throws Throwable;
	}
	
	public interface SignalVoidCallback {
		void call() throws Throwable;
	}
	
	public class SignalRegistration {
		public class SignalMessage {
			protected Signal<?> signal;
			
			public SignalMessage(Signal<?> signal) {
				this.signal = signal;
			}
			
			public SignalRegistration registration() {
				return SignalRegistration.this;
			}
			
			public Signal<?> signal() {
				return signal;
			}
			
			public String name() {
				return signal.name();
			}
			
			public Object argument() {
				return signal.argument();
			}
		}
		
		protected SignalCallback callback;
		protected String signal;
		
		public SignalRegistration(String signal, SignalCallback callback) {
			this.signal   = signal;
			this.callback = callback;
		}
		
		public String signal() {
			return signal;
		}
		
		public SignalCallback callback() {
			return callback;
		}
		
		public void invoke(Signal<?> signal) {
			try {
				callback.call(new SignalMessage(signal));
			} catch(SignalRegistrationCancelledException exc) {
				cancel();
			} catch(Throwable exc) {
				program.exception(exc);
			}
		}
		
		public void cancel() {
			unregister(this);
		}
	}

	protected ConcurrentHashMap<String, ConcurrentLinkedDeque<SignalRegistration>> registrations = new ConcurrentHashMap<>();
	protected Program program;
	
	public SignalHub(Program program) {
		this.program = program;
	}
	
	public Program program() {
		return program;
	}
	
	public SignalRegistration handle(String signal, SignalCallback callback) {
		SignalRegistration reg = new SignalRegistration(signal, callback);
		registrations.putIfAbsent(signal, new ConcurrentLinkedDeque<>());
		registrations.get(signal).add(reg);
		
		return reg;
	}
	
	public SignalRegistration handle(String signal, Object expectedArgument, SignalCallback callback) {
		SignalRegistration reg = new SignalRegistration(signal, (sigMsg)->{
			boolean nullityMatches = (expectedArgument  == null)
					              == (sigMsg.argument() == null);
			
			if(!nullityMatches)                             return;
			if(expectedArgument == null)                    return;
			if(!sigMsg.argument().equals(expectedArgument)) return;
			
			callback.call(sigMsg);
		});
		
		registrations.putIfAbsent(signal, new ConcurrentLinkedDeque<>());
		registrations.get(signal).add(reg);
		
		return reg;
	}
	
	public SignalRegistration handle(String signal, SignalVoidCallback callback) {
		return handle(signal, (arg)->callback.call());
	}
	
	public SignalRegistration handle(String signal, Object expectedArgument, SignalVoidCallback callback) {
		return handle(signal, expectedArgument, (arg)->callback.call());
	}
	
	public Deque<SignalRegistration> handlersForSignal(String signal) {
		if(!registrations.containsKey(signal)) return new ConcurrentLinkedDeque<>();
		
		return registrations.get(signal);
	}
	
	public Collection<String> handledSignals() {
		return registrations.keySet();
	}
	
	public SignalHub unregister(SignalRegistration handler) {
		ConcurrentLinkedDeque<SignalRegistration> list = registrations.get(handler.signal());
		if(list == null) return this;
		
		list.remove(handler);
		/* TODO: it'd be nice to prune empty signals from the map, but this has to be done
		 * carefully -- there's a possible race condition with adding/unregistering at the
		 * same time, and this efficiency boost may not be worth the cost of adding locks. 
		 */
		
		return this;
	}
	
	public SignalHub signal(String signal) {
		return signal(new Signal<Object>(signal, null));
	}
	
	public SignalHub signal(String signal, Object argument) {
		return signal(new Signal<Object>(signal, argument));
	}
	
	public SignalHub signal(Signal<?> signal) {
		ConcurrentLinkedDeque<SignalRegistration> list = registrations.get(signal.name());
		if(list == null) return this;
		
		for(SignalRegistration reg : list) {
			reg.invoke(signal);
		}
		
		return this;
	}
}
