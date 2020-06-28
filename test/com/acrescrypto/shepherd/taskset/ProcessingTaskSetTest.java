package com.acrescrypto.shepherd.taskset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.acrescrypto.shepherd.core.Program;
import static com.acrescrypto.shepherd.TestTools.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessingTaskSetTest {
	Program                          program;
	ProcessingTaskSet<Integer,Double> taskset;
	
	@BeforeEach
	public void beforeEach() {
		program = testProgram();
		taskset = new ProcessingTaskSet<Integer,Double>("test taskset").pool(program.pool());
	}
	
	@AfterEach
	public void afterEach() throws TimeoutException, InterruptedException {
		finishProgram(program);
	}
	
	public void setupBasicTest() {
		for(int i = 0; i < 10; i++) {
			taskset.add(i);
		}
		
		taskset.lambda((n)->3.5 * n);
	}
	
	@Test
	public void testInvokesLambdaForEachArgument() {
		ConcurrentHashMap<Integer,Boolean> seen = new ConcurrentHashMap<>();
		taskset.lambda((x)->{
			seen.put(x, true);
			return 0.0;
		});
		
		int count = 10;
		for(int i = 0; i < count; i++) {
			taskset.add(i);
		}
		
		taskset.run();
		waitFor(()->seen.size() == count);
		for(int i = 0; i < 10; i++) {
			assertTrue(seen.containsKey(i));
		}
	}
	
	@Test
	public void testInvokesArgumentsSimultaneously() throws InterruptedException, BrokenBarrierException, TimeoutException {
		int count = 3;
		taskset.pool().workers(count);
		CyclicBarrier barrier = new CyclicBarrier(1 + count);
		
		for(int i = 0; i < count; i++) {
			taskset.add(1);
		}
		
		taskset.lambda((x)->{
			barrier.await(1000, TimeUnit.MILLISECONDS);
			return 0.0;
		}).run();
		
		barrier.await(1000, TimeUnit.MILLISECONDS);
		// if we don't time out, everyone hit the barrier simultaneously
	}
	
	@Test
	public void testAcceptsAsynchronousLambda() throws InterruptedException, BrokenBarrierException, TimeoutException {
		CyclicBarrier barrier = new CyclicBarrier(2);
		
		taskset
		  .lambda((task, x)->{
			new SimpleTaskSet("asynchronous worker")
			    .delegate()
				.task(()->{
					barrier.await(1000, TimeUnit.MILLISECONDS);
					task.finish(3.0);
			  }).run();
		}).add(0)
		  .run();
		
		holdFor(100, ()->!taskset.isFinished());
		barrier.await(1000, TimeUnit.MILLISECONDS);
		waitFor(()->taskset.isFinished());
	}
	
	@Test
	public void testEachInvokedForEveryArgumentAndResult() throws InterruptedException, TimeoutException {
		setupBasicTest();
		ConcurrentHashMap<Integer,Double> seen = new ConcurrentHashMap<>();
		
		taskset
			.each((x, y)->seen.put(x, y))
			.run()
			.await(1000);
		
		assertEquals(taskset.arguments().size(), seen.size());
		for(int i = 0; i < taskset.arguments().size(); i++) {
			assertTrue(seen.containsKey(i));
			assertEquals(3.5*i, seen.get(i));
		}
	}
	
	@Test
	public void testEachToleratesNullArgument() throws InterruptedException, TimeoutException {
		double e = 2.718281828459045;
		
		taskset
			.lambda( (x) -> e )
			.add((Integer) null)
			.each((arg, res)->{
				assertEquals(null, arg);
				assertEquals(e, res);
		  }).run()
			.await(1000);
	}
	
	@Test
	public void testEachToleratesNullResult() throws InterruptedException, TimeoutException {
		taskset
			.lambda( (x) -> null )
			.add(1)
			.each((arg, result)->{
				assertEquals(   1,    arg);
				assertEquals(null, result);
		  }).run()
			.await(1000);
	}
	
	@Test
	public void testEachToleratesEmptyArgumentList() throws InterruptedException, TimeoutException {
		taskset
			.lambda( (x) -> null )
			.each((arg, res)->{
				fail("should not invoke each handler");
		  }).run()
			.await(1000);
	}
	
	@Test
	public void testListInvokedOnce() throws InterruptedException, TimeoutException {
		AtomicInteger counter = new AtomicInteger();
		
		setupBasicTest();
		taskset
			.list((list)->counter.incrementAndGet())
			.run()
			.await(1000);
		
		assertEquals(1, counter.get());
	}
	
	@Test
	public void testListIncludesAllArgumentsInInsertionOrder() throws InterruptedException, TimeoutException {
		setupBasicTest();
		taskset
			.list((list)->{
				assertEquals(10, list.size());
				int i = 0;
				
				for(double result : list) {
					assertEquals(3.5*i, result);
					i++;
				}
		  }).run()
			.await(1000);
	}
	
	@Test
	public void testListToleratesNullArgument() throws InterruptedException, TimeoutException {
		taskset
			.lambda((x) -> 0.0)
			.add((Integer) null)
			.list((list)->{
				assertEquals(1  , list.size());
				assertEquals(0.0, list.getFirst());
		  }).run()
			.await(1000);
	}
	
	@Test
	public void testListToleratesNullResult() throws InterruptedException, TimeoutException {
		taskset
			.lambda((x) -> null)
			.add(0)
			.list((list)->{
				assertEquals(   1, list.size());
				assertEquals(null, list.getFirst());
		  }).run()
			.await(1000);
	}
	
	@Test
	public void testListToleratesEmptyArgumentList() throws InterruptedException, TimeoutException {
		taskset
			.lambda((x) -> null)
			.list((list)->{
				assertEquals(   0, list.size());
		  }).run()
			.await(1000);
	}
	
	@Test
	public void testMapInvokedOnce() throws InterruptedException, TimeoutException {
		AtomicInteger counter = new AtomicInteger();
		setupBasicTest();
		taskset
			.map((map)->counter.incrementAndGet())
			.run()
			.await(1000);
		
		assertEquals(1, counter.get());
	}
	
	@Test
	public void testListIncludesAllArguments() throws InterruptedException, TimeoutException {
		setupBasicTest();
		taskset
			.map((map)->{
				assertEquals(10, map.size());
				for(int i = 0; i < 10; i++) {
					assertTrue(map.containsKey(i));
					assertEquals(3.5*i, map.get(i));
				}
		  }).run()
			.await(1000);
	}
	
	public void testMapToleratesNullArgument() throws InterruptedException, TimeoutException {
		taskset
			.lambda((x) -> 0.0)
			.add((Integer) null)
			.map((map)->{
				assertEquals(1  , map.size());
				assertEquals(0.0, map.get(null));
		  }).run()
			.await(1000);
	}
	
	@Test
	public void testMapToleratesNullResult() throws InterruptedException, TimeoutException {
		taskset
			.lambda((x) -> null)
			.add(0)
			.map((map)->{
				assertEquals(   1, map.size());
				assertEquals(null, map.get(0));
		  }).run()
			.await(1000);
	}
	
	@Test
	public void testMapToleratesEmptyArgumentList() throws InterruptedException, TimeoutException {
		taskset
			.lambda((x) -> null)
			.map((map)->{
				assertEquals(   0, map.size());
		  }).run()
			.await(1000);
	}
	
	@Test
	public void testInvokesMultipleAfterCallbacks() throws InterruptedException, TimeoutException {
		AtomicInteger count = new AtomicInteger();
		setupBasicTest();
		
		taskset
			.after(()        -> count.incrementAndGet())
			.each ((arg, res)-> count.incrementAndGet())
			.list ((list)    -> count.incrementAndGet())
			.map  ((map)     -> count.incrementAndGet())
			.run()
			.await(1000);
		assertEquals(3 + taskset.arguments().size(), count.get());
	}
}
