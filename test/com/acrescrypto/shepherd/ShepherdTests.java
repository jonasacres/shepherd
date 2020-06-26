package com.acrescrypto.shepherd;

import org.junit.platform.runner.JUnitPlatform;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
@SelectPackages({
	"com.acrescrypto.shepherd.worker",
	"com.acrescrypto.shepherd.taskset"
}) 

public class ShepherdTests {
	
}
