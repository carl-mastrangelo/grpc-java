/*
 * Copyright 2016, gRPC Authors All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Unit tests for {@link ChannelExecutor}.
 */
@RunWith(JUnit4.class)
public class ChannelExecutorTest {
  private final ThreadlessExec executor = new ThreadlessExec();

  @Mock
  private Runnable task1;

  @Mock
  private Runnable task2;

  @Mock
  private Runnable task3;

  @Before public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void singleThread() {

    executor.execute(task1);
    executor.execute(task2);
    InOrder inOrder = inOrder(task1, task2, task3);
    inOrder.verifyNoMoreInteractions();
    executor.resume();
    inOrder.verify(task1).run();
    inOrder.verify(task2).run();

    executor.suspend();
    executor.execute(task3);
    inOrder.verifyNoMoreInteractions();
    executor.resume();
    inOrder.verify(task3).run();
  }

  @Test
  public void multiThread() throws Exception {
    InOrder inOrder = inOrder(task1, task2);

    final CountDownLatch task1Added = new CountDownLatch(1);
    final CountDownLatch task1Running = new CountDownLatch(1);
    final CountDownLatch task1Proceed = new CountDownLatch(1);
    final CountDownLatch sideThreadDone = new CountDownLatch(1);
    final AtomicReference<Thread> task1Thread = new AtomicReference<Thread>();
    final AtomicReference<Thread> task2Thread = new AtomicReference<Thread>();

    doAnswer(new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) {
          task1Thread.set(Thread.currentThread());
          task1Running.countDown();
          try {
            assertTrue(task1Proceed.await(5, TimeUnit.SECONDS));
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          return null;
        }
      }).when(task1).run();

    doAnswer(new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) {
          task2Thread.set(Thread.currentThread());
          return null;
        }
      }).when(task2).run();

    Thread sideThread = new Thread() {
        @Override
        public void run() {
          executor.suspend();
          executor.execute(task1);
          task1Added.countDown();
          executor.resume();
          sideThreadDone.countDown();
        }
      };
    sideThread.start();

    assertTrue(task1Added.await(5, TimeUnit.SECONDS));
    executor.suspend();
    executor.execute(task2);
    assertTrue(task1Running.await(5, TimeUnit.SECONDS));
    // This will do nothing because task1 is running until task1Proceed is set
    executor.resume();

    inOrder.verify(task1).run();
    inOrder.verifyNoMoreInteractions();

    task1Proceed.countDown();
    // drain() on the side thread has returned, which runs task2
    assertTrue(sideThreadDone.await(5, TimeUnit.SECONDS));
    inOrder.verify(task2).run();

    assertSame(sideThread, task1Thread.get());
    assertSame(sideThread, task2Thread.get());
  }

  @Test
  public void taskThrows() {
    InOrder inOrder = inOrder(task1, task2, task3);
    doAnswer(new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) {
          throw new RuntimeException("Simulated");
        }
      }).when(task2).run();
    executor.suspend();
    executor.execute(task1);
    executor.execute(task2);
    executor.execute(task3);
    executor.resume();
    inOrder.verify(task1).run();
    inOrder.verify(task2).run();
    inOrder.verify(task3).run();
  }

  @Test
  public void allTasksExecute() throws Exception {
    final class Counter {
      long count;
    }
    final Counter counter = new Counter();
    int threads = 8;
    final long rounds = 1000000;
    ExecutorService es = Executors.newFixedThreadPool(threads);
    for (int i = 0; i < threads - 1; i++) {
      es.execute(new Runnable() {

        @Override
        public void run() {
          for (int k = 0; k < rounds; k++) {
            int sus = ThreadLocalRandom.current().nextInt(4);
            for (int m = 0; m < sus; m++) {
              executor.suspend();
            }
            while (sus != 0) {
              if (ThreadLocalRandom.current().nextInt(4) == 0) {
                executor.suspend();
                sus++;
              } else {
                executor.resume();
                sus--;
              }
            }
            executor.execute(new Runnable() {
              @Override
              public void run() {
                counter.count++;
              }
            });
          }
        }
      });
    }
    es.execute(new Runnable() {
      @Override
      public void run() {
        for (int k = 0; k < rounds; k++) {
          executor.execute(new Runnable() {
            @Override
            public void run() {
              counter.count++;
            }
          });
          // Once in a while, cut to the front of the line.
          if (ThreadLocalRandom.current().nextInt(128) == 0) {
            executor.forceDrain();
          }
        }
      }
    });
    es.shutdown();
    assertTrue(es.awaitTermination(20, TimeUnit.SECONDS));

    assertEquals(threads * rounds, counter.count);
  }
}
