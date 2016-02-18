/*
 * Copyright (c) 2012-2016 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.spotify.netty.util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BatchFlusherTest {

  @Mock Channel channel;
  @Mock EventLoop eventLoop;

  private final Queue<Runnable> runnableQueue = new LinkedBlockingQueue<Runnable>();

  private Answer queueRunnable = new Answer() {
    @Override
    public Object answer(final InvocationOnMock invocation) throws Throwable {
      runnableQueue.add(invocation.getArgumentAt(0, Runnable.class));
      return null;
    }
  };

  @Before
  public void setUp() throws Exception {
    doAnswer(queueRunnable).when(eventLoop).execute(any(Runnable.class));
  }

  @Test
  public void testFlushOutsideEventLoop() throws Exception {
    when(channel.eventLoop()).thenReturn(eventLoop);
    final BatchFlusher flusher = BatchFlusher.of(channel);

    // Flush from other thread
    when(eventLoop.inEventLoop()).thenReturn(false);

    // First flush, verify that the event loop is invoked and that the channel is not yet flushed
    flusher.flush();
    verify(eventLoop, times(1)).execute(any(Runnable.class));
    verify(channel, never()).flush();

    // Second flush, verify that the event loop is _not_ invoked a second time and that the channel is not yet flushed
    flusher.flush();
    verify(eventLoop, times(1)).execute(any(Runnable.class));
    verify(channel, never()).flush();

    // Run the event loop
    final int runnables = runEventLoop();

    // Verify that the channel was flushed
    verify(channel, timeout(1000).times(1)).flush();

    // Third flush, verify that the event loop was invoked again and that the channel is not yet flushed again
    flusher.flush();
    verify(eventLoop, times(runnables + 1)).execute(any(Runnable.class));
    verify(channel, times(1)).flush();

    // Run the event loop again
    runEventLoop();

    // Verify that the channel was flushed again
    verify(channel, timeout(1000).times(2)).flush();
  }

  private int runEventLoop() {
    int n = 0;
    while (true) {
      final Runnable runnable = runnableQueue.poll();
      if (runnable == null) {
        break;
      }
      n++;
      runnable.run();
    }
    return n;
  }

  @Test
  public void testFlushInEventLoop() throws Exception {
    when(channel.eventLoop()).thenReturn(eventLoop);
    final int maxPending = 2;
    final BatchFlusher flusher = BatchFlusher.of(channel, maxPending);

    // Flush on event loop thread
    when(eventLoop.inEventLoop()).thenReturn(true);

    // First flush, verify that the channel is not yet flushed
    flusher.flush();
    verify(channel, never()).flush();

    // Second flush, verify that the channel is flushed
    flusher.flush();
    verify(channel, times(1)).flush();

    // First flush, verify that the channel is not yet flushed again
    flusher.flush();
    verify(channel, times(1)).flush();

    // Second flush, verify that the channel is flushed again
    flusher.flush();
    verify(channel, times(2)).flush();
  }
}