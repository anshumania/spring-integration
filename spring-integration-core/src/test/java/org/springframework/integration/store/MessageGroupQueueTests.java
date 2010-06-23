/*
 * Copyright 2002-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.integration.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.springframework.integration.core.Message;
import org.springframework.integration.message.StringMessage;

/**
 * @author Dave Syer
 * @since 2.0
 * 
 */
public class MessageGroupQueueTests {
	
	static final Log logger = LogFactory.getLog(MessageGroupQueueTests.class);

	@Test
	public void testPutAndPoll() throws Exception {
		MessageGroupQueue queue = new MessageGroupQueue(new SimpleMessageStore(), "FOO");
		queue.put(new StringMessage("foo"));
		Message<?> result = queue.poll(100, TimeUnit.MILLISECONDS);
		assertNotNull(result);
	}

	@Test
	public void testSize() throws Exception {
		MessageGroupQueue queue = new MessageGroupQueue(new SimpleMessageStore(), "FOO");
		queue.put(new StringMessage("foo"));
		assertEquals(1, queue.size());
		queue.poll(100, TimeUnit.MILLISECONDS);
		assertEquals(0, queue.size());
	}

	@Test
	public void testCapacityAfterExpiry() throws Exception {
		SimpleMessageStore messageGroupStore = new SimpleMessageStore();
		MessageGroupQueue queue = new MessageGroupQueue(messageGroupStore, "FOO", 2);
		queue.put(new StringMessage("foo"));
		assertEquals(1, queue.remainingCapacity());
		queue.put(new StringMessage("bar"));
		assertEquals(0, queue.remainingCapacity());
		Message<?> result = queue.poll(100, TimeUnit.MILLISECONDS);
		assertNotNull(result);
		assertEquals(1, queue.remainingCapacity());
	}

	@Test
	public void testCapacityExceeded() throws Exception {
		SimpleMessageStore messageGroupStore = new SimpleMessageStore();
		MessageGroupQueue queue = new MessageGroupQueue(messageGroupStore, "FOO", 1);
		queue.put(new StringMessage("foo"));
		assertFalse(queue.offer(new StringMessage("bar"), 100, TimeUnit.MILLISECONDS));
	}

	@Test
	public void testPutAndTake() throws Exception {
		MessageGroupQueue queue = new MessageGroupQueue(new SimpleMessageStore(), "FOO");
		queue.put(new StringMessage("foo"));
		Message<?> result = queue.take();
		assertNotNull(result);
	}

	@Test
	public void testConcurrentAccess() throws Exception {

		SimpleMessageStore messageGroupStore = new SimpleMessageStore();
		final MessageGroupQueue queue = new MessageGroupQueue(messageGroupStore, "FOO");
		CompletionService<Boolean> completionService = new ExecutorCompletionService<Boolean>(Executors
				.newCachedThreadPool());

		int concurrency = 30;
		final int maxPerTask = 20;
		final Set<String> set = new HashSet<String>();

		for (int i = 0; i < concurrency; i++) {

			final int big = i;		
	
			completionService.submit(new Callable<Boolean>() {
				public Boolean call() throws Exception {
					boolean result = true;
					for (int j = 0; j < maxPerTask; j++) {
						result &= queue.add(new StringMessage("count=" + big + ":" + j));
						if (!result) {
							logger.warn("Failed to add");
						}
					}
					return result;
				}
			});

			completionService.submit(new Callable<Boolean>() {
				public Boolean call() throws Exception {
					boolean result = true;
					for (int j = 0; j < maxPerTask; j++) {
						@SuppressWarnings("unchecked")
						Message<String> item = (Message<String>) queue.poll(1, TimeUnit.SECONDS);
						set.add(item.getPayload());
						result &= item!=null;
						if (!result) {
							logger.warn("Failed to poll");
						}
					}
					return result;
				}
			});
			
			messageGroupStore.expireMessageGroups(-10000);
			
		}

		for (int j = 0; j < 2*concurrency; j++) {
			assertTrue(completionService.take().get());
		}
		
		// Ensure all items polled are unique
		assertEquals(concurrency*maxPerTask, set.size());
		
		assertEquals(0, queue.size());
		messageGroupStore.expireMessageGroups(-10000);
		assertEquals(Integer.MAX_VALUE, queue.remainingCapacity());

	}

}