package com.acrescrypto.shepherd;

public class Callbacks {
	public interface VoidCallback { void call() throws Exception; }
	public interface OpportunisticExceptionHandler { void handle(Throwable exc) throws Exception; }
	public interface ExceptionHandler { void exception(Throwable exc); }
}
