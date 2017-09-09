/*
 * Copyright 2017, gRPC Authors All rights reserved.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.errorprone.annotations.ForOverride;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Consolidates shutdown and termination logic.
 */
class Terminator {
  private static final Logger logger = Logger.getLogger(Terminator.class.getName());

  enum RunState {
    RUNNING,
    SHUTDOWN,
    TERMINATING,
    TERMINATED,
    ;
  }

  private final String debugString;
  private final AtomicReference<RunState> runState =
      new AtomicReference<RunState>(RunState.RUNNING);

  static Terminator newTerminator(String debugString) {
    return new Terminator(debugString);
  }

  private Terminator(String debugString) {
    this.debugString = checkNotNull(debugString, "debugString");
  }

  /**
   * Moves the runstate to shutdown.  Assumes that it will be called at most once.
   */
  final void shutdown() {
    if (transition(RunState.RUNNING, RunState.SHUTDOWN, false)) {
      onShutdown();
    }
  }

  /**
   * Moves the runstate to shutdown.  May be called more than once.
   */
  final boolean shutdownIdempotent() {
    boolean success;
    if ((success = transition(RunState.RUNNING, RunState.SHUTDOWN, true))) {
      onShutdown();
    }
    return success;
  }

  @ForOverride
  void onShutdown() {}

  final boolean isShutdown() {
    return runState.get().ordinal() >= RunState.SHUTDOWN.ordinal();
  }

  final void startTerminate() {
    if (transition(RunState.SHUTDOWN, RunState.TERMINATING, false)) {
      onTerminating();
    }
  }

  final boolean startTerminateIdempotent() {
    boolean success;
    if ((success = transition(RunState.SHUTDOWN, RunState.TERMINATING, true))) {
      onTerminating();
    }
    return success;
  }

  @ForOverride
  void onTerminating() {}

  final boolean isTerminating() {
    return runState.get().ordinal() >= RunState.TERMINATING.ordinal();
  }

  final void finishTerminate() {
    if (transition(RunState.TERMINATING, RunState.TERMINATED, false)) {
      synchronized (runState) {
        runState.notifyAll();
      }
      onTerminated();
    }
  }

  final boolean finishTerminateIdempotent() {
    boolean success;
    if ((success = transition(RunState.TERMINATING, RunState.TERMINATED, true))) {
      synchronized (runState) {
        runState.notifyAll();
      }
      onTerminated();
    }
    return success;
  }

  @ForOverride
  void onTerminated() {}

  final boolean isTerminated() {
    return runState.get() == RunState.TERMINATED;
  }

  final boolean awaitTermination(long timeout, TimeUnit timeUnit) throws InterruptedException {
    if (isTerminated()) {
      return true;
    }
    long nowNanos = System.nanoTime();
    long deadlineNanoes = nowNanos + timeUnit.toNanos(timeout);

    long sleepNanos = -1;

    synchronized (runState) {
      while (!isTerminated() && (sleepNanos = deadlineNanoes - System.nanoTime()) > 0) {
        runState.wait(TimeUnit.NANOSECONDS.toMillis(sleepNanos));
      }
    }

    return isTerminated();
  }

  private boolean transition(RunState from, RunState to, boolean idempotent) {
    if (runState.compareAndSet(from, to)) {
      return true;
    }
    RunState unexpectedState = runState.get();

    if (idempotent && !logger.isLoggable(Level.FINE)) {
      return false;
    }

    String fmt = "{0}: can't move from {1} to {2}, was {3}";
    Object[] args = new Object[] {debugString, from, to, unexpectedState};

    if (idempotent) {
      LogRecord lr = new LogRecord(Level.FINE, fmt);
      lr.setParameters(args);
      logger.log(lr);
      return false;
    }
    throw new IllegalStateException(String.format(fmt, args));
  }
}
