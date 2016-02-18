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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;

/**
 * A batcher of netty channel flushes for gathering writes into fewer syscalls.
 */
public class BatchFlusher {

  private static final int DEFAULT_MAX_PENDING = 64;

  private final Channel channel;
  private final EventLoop eventLoop;
  private final int maxPending;

  private final AtomicIntegerFieldUpdater<BatchFlusher> WOKEN =
      AtomicIntegerFieldUpdater.newUpdater(BatchFlusher.class, "woken");
  @SuppressWarnings("UnusedDeclaration") private volatile int woken;

  private int pending;

  /**
   * Used to flush all outstanding writes in the outbound channel buffer.
   */
  private final Runnable flush = new Runnable() {
    @Override
    public void run() {
      pending = 0;
      channel.flush();
    }
  };

  /**
   * Used to wake up the event loop and schedule a flush to be performed after all outstanding write
   * tasks are run. The outstanding write tasks must be allowed to run before performing the actual
   * flush in order to ensure that their payloads have been written to the outbound buffer.
   */
  private final Runnable wakeup = new Runnable() {
    @Override
    public void run() {
      woken = 0;
      eventLoop.execute(flush);
    }
  };

  /**
   * Create a new {@link BatchFlusher}.
   */
  private BatchFlusher(final Channel channel, final EventLoop eventLoop, final int maxPending) {
    this.channel = channel;
    this.maxPending = maxPending;
    this.eventLoop = eventLoop;
  }

  /**
   * Schedule an asynchronous opportunistically batching flush.
   */
  public void flush() {
    if (eventLoop.inEventLoop()) {
      pending++;
      if (pending >= maxPending) {
        pending = 0;
        channel.flush();
      }
    }
    if (woken == 0 && WOKEN.compareAndSet(this, 0, 1)) {
      woken = 1;
      eventLoop.execute(wakeup);
    }
  }

  /**
   * Create a new {@link BatchFlusher}.
   */
  public static BatchFlusher of(final Channel channel) {
    return of(channel, DEFAULT_MAX_PENDING);
  }

  /**
   * Create a new {@link BatchFlusher}.
   */
  public static BatchFlusher of(final Channel channel, final int maxPending) {
    return of(channel, channel.eventLoop(), maxPending);
  }

  /**
   * Create a new {@link BatchFlusher}.
   */
  public static BatchFlusher of(final Channel channel, final EventLoop eventLoop) {
    return of(channel, eventLoop, DEFAULT_MAX_PENDING);
  }

  /**
   * Create a new {@link BatchFlusher}.
   */
  public static BatchFlusher of(final Channel channel, final EventLoop eventLoop, final int maxPending) {
    return new BatchFlusher(channel, eventLoop, maxPending);
  }
}