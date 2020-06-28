package com.acrescrypto.shepherd.core;

public class Signal<T> {
	protected String name;
	protected T      argument;
	
	public Signal(String name) {
		this(name, null);
	}
	
	public Signal(String name, T argument) {
		this.name     = name;
		this.argument = argument;
	}
	
	public String name() {
		return name;
	}
	
	public T argument() {
		return argument;
	}
}
