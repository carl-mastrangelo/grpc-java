package io.grpc.internal;

import static com.google.common.base.Preconditions.checkState;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Created by notcarl on 9/8/17.
 */
final class ThreadlessExec implements Executor {

  private static final Logger logger = Logger.getLogger(ThreadlessExec.class.getName());

  private static final AtomicIntegerFieldUpdater<ThreadlessExec> runStateUpdater =
      AtomicIntegerFieldUpdater.newUpdater(ThreadlessExec.class, "runState");
  private static final int RUNNING_MASK = 0x40000000;
  private static final int STOPPED_MASK = 0x00000000;
  private static final int SUSPEND_MASK = RUNNING_MASK - 1;

  private final Queue<Runnable> runQueue = new ConcurrentLinkedQueue<Runnable>();
  private volatile int runState;

  public void suspend() {
    int old;
    do {
      old = runStateUpdater.get(this);
      checkState((old & SUSPEND_MASK) != SUSPEND_MASK, "Max number of suspends reached");
    } while (!runStateUpdater.compareAndSet(this, old, old + 1));
  }

  public void resume() {
    int old;
    do {
      old = runStateUpdater.get(this);
      checkState((old & SUSPEND_MASK) != 0, "Resume called too often");
    } while (!runStateUpdater.compareAndSet(this, old, old - 1));
    tryDrain();
  }

  private boolean canDrain() {
    return runStateUpdater.get(this) == STOPPED_MASK && !runQueue.isEmpty();
  }

  private void tryDrain() {
    if (!runStateUpdater.compareAndSet(this, STOPPED_MASK, RUNNING_MASK)) {
      return;
    }
    try {
      Runnable r;
      while (runStateUpdater.get(this) == RUNNING_MASK && (r = runQueue.poll()) != null) {
        try {
          r.run();
        } catch (RuntimeException e) {
          if (logger.isLoggable(Level.SEVERE)) {
            LogRecord lr = new LogRecord(Level.SEVERE, "Failure running {0}");
            lr.setLoggerName(logger.getName());
            lr.setParameters(new Object[]{r});
            lr.setThrown(e);
            logger.log(lr);
          }
        }
      }
    } finally {
      int old;
      do {
        old = runStateUpdater.get(this);
      } while (!runStateUpdater.compareAndSet(this, old, old & ~RUNNING_MASK));
    }
  }

  @Override
  public void execute(Runnable command) {
    runQueue.add(command);
    tryDrain();
  }
}
