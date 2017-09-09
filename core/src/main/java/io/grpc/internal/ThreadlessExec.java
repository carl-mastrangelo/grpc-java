package io.grpc.internal;

import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A threadless executor.
 */
@NotThreadSafe
abstract class ThreadlessExec implements Executor {

  protected static final Logger logger = Logger.getLogger(ThreadlessExec.class.getName());

  /**
   * Disables execution of submitted runnables as soon as possible.  May be called multiple times.
   * Execution will continue after {@link #resume} has been called an equal number of times.
   * Implementations may start early if {@link #forceDrain} is called.
   */
  public abstract void suspend();

  public abstract void resume();

  public abstract void forceDrain();

  public abstract boolean isSuspended();

  protected boolean runOne(Runnable r) {
    if (r == null) {
      return false;
    }
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
    return true;
  }
}
