package io.grpc.internal;

import static com.google.common.base.Preconditions.checkState;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Created by notcarl on 9/9/17.
 */
@ThreadSafe
final class ConcurrentThreadlessExecutor extends ThreadlessExec {
  private static final AtomicIntegerFieldUpdater<ConcurrentThreadlessExecutor> runStateUpdater =
      AtomicIntegerFieldUpdater.newUpdater(ConcurrentThreadlessExecutor.class, "runState");
  private static final int RUN_STATE_BITS = 1;
  private static final int RUN_MASK = -1 << (Integer.SIZE - RUN_STATE_BITS);
  private static final int STOPPED = 0 << (Integer.SIZE - RUN_STATE_BITS);
  private static final int RUNNING = 1 << (Integer.SIZE - RUN_STATE_BITS);
  private static final int SUSPEND_MASK = ~RUN_MASK;

  private final Queue<Runnable> runQueue = new ConcurrentLinkedQueue<Runnable>();
  private volatile int runState;

  @Override
  public void suspend() {
    int old;
    do {
      old = runStateUpdater.get(this);
      checkState((old & SUSPEND_MASK) != SUSPEND_MASK, "Max number of suspends reached");
    } while (!runStateUpdater.compareAndSet(this, old, old + 1));
  }

  @Override
  public void resume() {
    int old;
    do {
      old = runStateUpdater.get(this);
      checkState((old & SUSPEND_MASK) != 0, "Resume called too often");
    } while (!runStateUpdater.compareAndSet(this, old, old - 1));
    tryDrain();
  }

  @Override
  public void forceDrain() {
    suspend();
    try {
      int old;
      int oldSuspend;
      do {
        old = runStateUpdater.get(this);
        oldSuspend = (old & SUSPEND_MASK);
      } while (!runStateUpdater.compareAndSet(this, STOPPED | oldSuspend, RUNNING | oldSuspend));

      while (runOne(runQueue.poll())) {}

      do {
        old = runStateUpdater.get(this);
        oldSuspend = (old & SUSPEND_MASK);
      } while (!runStateUpdater.compareAndSet(this, RUNNING | oldSuspend, STOPPED | oldSuspend));
    } finally {
      resume();
    }
  }

  private void tryDrain() {
    if (!runStateUpdater.compareAndSet(this, STOPPED, RUNNING)) {
      return;
    }
    try {
      while (runStateUpdater.get(this) == RUNNING && runOne(runQueue.poll())) {}
    } finally {
      int old;
      do {
        old = runStateUpdater.get(this);
        assert (old & RUN_MASK) == RUNNING;
      } while (!runStateUpdater.compareAndSet(this, old, STOPPED | (old & SUSPEND_MASK)));
    }
  }

  @Override
  public boolean isSuspended() {
    return (runStateUpdater.get(this) & SUSPEND_MASK) != 0;
  }

  @Override
  public void execute(Runnable command) {
    runQueue.add(command);
    tryDrain();
  }
}
