package com.acrescrypto.shepherd.exceptions;

public class TaskSetRequiresTagException extends Exception {
	private static final long serialVersionUID = 1L;
	protected Object tag;
	
	public TaskSetRequiresTagException(Object tag) {
		this.tag = tag;
	}
	
	public Object tag() {
		return tag;
	}
}