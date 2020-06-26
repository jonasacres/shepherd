module com.acrescrypto.shepherd {
	requires junit;
	requires org.junit.jupiter.api;
	requires org.junit.platform.runner;
	requires org.junit.platform.suite.api;
	requires java.base;
	
	exports com.acrescrypto.shepherd;
	exports com.acrescrypto.shepherd.core;
	exports com.acrescrypto.shepherd.exceptions;
	exports com.acrescrypto.shepherd.taskset;
	exports com.acrescrypto.shepherd.worker;
	
	opens com.acrescrypto.shepherd.taskset;
	opens com.acrescrypto.shepherd.worker;
}