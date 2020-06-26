package com.acrescrypto.shephard.core;

import static org.junit.jupiter.api.Assertions.*;
import static com.acrescrypto.shepherd.TestTools.*;

import java.util.Collection;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.acrescrypto.shepherd.core.Program;
import com.acrescrypto.shepherd.core.SignalHub;
import com.acrescrypto.shepherd.core.SignalHub.SignalRegistration;
import com.acrescrypto.shepherd.exceptions.SignalRegistrationCancelledException;

public class SignalHubTest {
	
	protected Program   program;
	protected SignalHub hub;
	
	@BeforeEach
	public void beforeEach() {
		program = new Program();
		hub     = new SignalHub(program);
		program.hub(hub);
	}
	
	@AfterEach
	public void afterEach() throws TimeoutException, InterruptedException {
		program.stop(1000);
	}
	
	@Test
	public void testProgramGivesReferenceToParentProgram() {
		assertEquals(program, hub.program());
	}
	
	@Test
	public void testHandleAllowsRegistrationOfSignalCallbackWithoutArgument() {
		hub.handle("signal", (arg)->{});
		assertEquals(1, hub.handlersForSignal("signal").size());
	}
	
	@Test
	public void testHandleAllowsRegistrationOfVoidCallbackWithoutArgument() {
		hub.handle("signal", ()->{});
		assertEquals(1, hub.handlersForSignal("signal").size());
	}
	
	@Test
	public void testHandleAllowsRegistrationOfSignalCallbackWithArgument() {
		hub.handle("signal", this, (arg)->{});
		assertEquals(1, hub.handlersForSignal("signal").size());
	}
	
	@Test
	public void testHandleAllowsRegistrationOfVoidCallbackWithArgument() {
		hub.handle("signal", this, ()->{});
		assertEquals(1, hub.handlersForSignal("signal").size());
	}
	
	@Test
	public void testCallbackWithArgumentInvokedIfArgumentEqualsFilter() {
		AtomicBoolean invoked = new AtomicBoolean();
		String argA = "test", argB = "test";
		
		hub.handle("signal", argA, (arg)->{
			invoked.set(true);
		});
		
		hub.signal("signal", argB);
		waitFor(100, ()->invoked.get());
	}
	
	@Test
	public void testCallbackWithArgumentNotInvokedIfArgumentDoesntEqualsFilter() {
		AtomicBoolean invoked = new AtomicBoolean();
		String argA = "test", argB = "different";
		
		hub.handle("signal", argA, (arg)->{
			invoked.set(true);
		});
		
		hub.signal("signal", argB);
		holdFor(20, ()->invoked.get() == false);
	}
	
	@Test
	public void testCallbackWithoutArgumentSignalsWithArgument() {
		AtomicBoolean invoked = new AtomicBoolean();
		String str = "test";
		
		hub.handle("signal", (arg)->{
			invoked.set(true);
		});
		
		hub.signal("signal", str);
		waitFor(100, ()->invoked.get());
	}
	
	@Test
	public void testCallbackWithoutArgumentSignalsWithNullArgument() {
		AtomicBoolean invoked = new AtomicBoolean();
		
		hub.handle("signal", (arg)->{
			invoked.set(true);
		});
		
		hub.signal("signal");
		waitFor(100, ()->invoked.get());
	}
	
	@Test
	public void testSignalWithoutArgumentSignalsWithNullArgument() {
		AtomicBoolean matched = new AtomicBoolean();
		
		hub.handle("signal", (arg)->{
			matched.set(arg == null);
		});
		
		hub.signal("signal");
		waitFor(100, ()->matched.get());
	}
	
	@Test
	public void testSignalWithArgumentPassesArgument() {
		AtomicBoolean matched = new AtomicBoolean();
		Object argument = new Object();
		
		hub.handle("signal", (arg)->{
			matched.set(arg == argument);
		});
		
		hub.signal("signal", argument);
		waitFor(100, ()->matched.get());
	}
	
	@Test
	public void testUnregisterStopsFutureInvocationsOfCallback() {
		AtomicBoolean invoked = new AtomicBoolean();
		
		SignalRegistration reg = hub.handle("signal", ()->invoked.set(true));
		hub.unregister(reg);
		hub.signal("signal");
		
		holdFor(20, ()->invoked.get() == false);
	}
	
	@Test
	public void testThrowingSignalRegistrationCancelledExceptionUnregistersCallback() {
		AtomicInteger count = new AtomicInteger();
		
		hub.handle("signal", ()->{
			count.getAndIncrement();
			throw new SignalRegistrationCancelledException();
		});
		
		hub.signal("signal");
		waitFor(20, ()->count.get() == 1); // should increment and cancel
		
		hub.signal("signal");
		holdFor(20, ()->count.get() == 1); // shouldn't invoke so count stays at 1
	}
	
	@Test
	public void testHandledSignalsListsRegisteredSignals() {
		hub.handle("a", ()->{});
		hub.handle("b", ()->{});
		
		Collection<String> signals = hub.handledSignals();
		assertEquals(2, signals.size());
		assertTrue(signals.contains("a"));
		assertTrue(signals.contains("b"));
	}
	
	@Test
	public void testExceptionsInHandlersPassedToProgramExceptionHandler() {
		AtomicReference<Object> wrapper = new AtomicReference<>();
		
		program.onException((exc)->wrapper.set(exc));
		hub.handle("boom", ()->{ throw new RuntimeException(); });
		hub.signal("boom");
		
		waitFor(100, ()->wrapper.get() != null && wrapper.get() instanceof RuntimeException);
	}
}
