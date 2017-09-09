package io.grpc.internal;

import static com.google.common.base.Preconditions.checkState;

import com.google.errorprone.annotations.ForOverride;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by notcarl on 9/9/17.
 */
abstract class DirectThreadlessExecutor extends ThreadlessExec {

  private static final class Foo extends DirectThreadlessExecutor{

    private final BlockingQueue<Runnable> queue = new LinkedBlockingDeque<Runnable>();

    @Override
    protected boolean hasQueue() {
      return true;
    }

    @Override
    protected Queue<Runnable> getQueue() {
      return queue;
    }

    /**
     * Waits for at least one item to be present
     */
    @Override
    protected void runWhileNotSuspended() {
      if (isSuspended()) {
        return;
      }

      try {
        runOne(queue.take());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
      super.runWhileNotSuspended();
    }
  }

  private boolean running;
  private int suspensions;

  @Override
  public final void suspend() {
    checkState(suspensions++ != Integer.MAX_VALUE, "suspend() called too many times");
  }

  @Override
  public final void resume() {
    checkState(suspensions-- != 0, "resume() called too many times");
    maybeDrainQueue();
  }

  @Override
  public boolean isSuspended() {
    return suspensions != 0;
  }

  @Override
  public final void forceDrain() {
    if (!hasQueue()) {
      return;
    }
    boolean oldRunning = running;
    try {
      while (runOne(getQueue().poll())) {}
    } finally {
      running = oldRunning;
    }
  }

  protected abstract boolean hasQueue();
  protected abstract Queue<Runnable> getQueue();

  private void maybeDrainQueue() {
    if (running || !hasQueue()) {
      return;
    }
    running = true;
    try {
      runWhileNotSuspended();
    } finally {
      running = false;
    }
  }

  @ForOverride
  protected void runWhileNotSuspended() {
    while (!isSuspended() && runOne(getQueue().poll())) {}
  }

  @Override
  public void execute(Runnable command) {
    if (!running && !isSuspended()) {
      try {
        running = true;
        runOne(command);
      } finally {
        running = false;
      }
      return;
    } else {
      getQueue().add(command);
    }
  }
}
