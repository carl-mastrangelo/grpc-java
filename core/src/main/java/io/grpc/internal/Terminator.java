package io.grpc.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Created by  on 9/7/17.
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

  @Nullable
  private final String debugString;
  private final AtomicReference<RunState> runState = new AtomicReference(RunState.RUNNING);

  Terminator(@Nullable String debugString) {
    this.debugString = debugString;
  }

  /**
   * Idempotent.
   */
  final void shutdown() {
    if (runState.compareAndSet(RunState.RUNNING, RunState.SHUTDOWN)) {
      onShutdown();
    } else if (logger.isLoggable(Level.FINE)) {
      logger.log(
          Level.FINE,
          "Shutdown of {0} already at {1}",
          new Object[] {debugString, runState.get()});
    }
  }

  void onShutdown() {}

  /**
   * Not Idempotent.
   */
  final void startTerminate() {
    if (runState.compareAndSet(RunState.SHUTDOWN, RunState.TERMINATING)) {
      onTerminating();
    } else if (logger.isLoggable(Level.SEVERE)) {
      LogRecord lr = new LogRecord(Level.SEVERE, "Termination of {0} started already at {1}");
      lr.setParameters(new Object[] {debugString, runState.get()});
      lr.setThrown(new IllegalStateException());
      logger.log(lr);
    }
  }

  void onTerminating() {}
}
