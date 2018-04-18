/*
 * Copyright 201, gRPC Authors All rights reserved.
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

package io.grpc.stub;

import com.google.errorprone.annotations.DoNotMock;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * A Non-blocking, synchronous RPC.
 */
@CheckReturnValue
@DoNotMock("or else!")
public abstract class SyncCall<ReqT, RespT> implements Closeable {

  SyncCall() {}

  /**
   * Tries to get a response from the remote endpoint, waiting until a message is available.  If
   * the call is completed, or the remote endpoint half-closes, this may return null.
   *
   * @return A response or {@code null} if not available.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  @Nullable
  public abstract RespT poll();

  /**
   * Tries to get a response from the remote endpoint, waiting up until the given timeout.
   *
   * @param timeout how long to wait before returning, in units of {@code unit}
   * @param unit a {@link TimeUnit} determining how to interpret {@code timeout}.
   * @return A response or {@code null} if not available.  Always returns {@code null} after the
   *         remote endpoint has half-closed.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  @Nullable
  public abstract RespT poll(long timeout, TimeUnit unit);

  /**
   * Tries to get a response from the remote endpoint, waiting until a message is available.  If
   * the call is completed, or the remote endpoint half-closes, this may return null.  Unlike
   * {@link #poll()}, if the calling thread is interrupted, it will be ignored.
   *
   * @return A response or {@code null} if not available.  Always returns {@code null} after the
   *         remote endpoint has half-closed.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  @Nullable
  public abstract RespT pollUninterruptibly();

  /**
   * Tries to get a response from the remote endpoint, waiting up until the given timeout.  Unlike
   * {@link #poll(long, TimeUnit)}, if the calling thread is interrupted, it will be ignored.
   *
   * @param timeout how long to wait before returning, in units of {@code unit}
   * @param unit a {@link TimeUnit} determining how to interpret {@code timeout}.
   * @return A response or {@code null} if not available.  Always returns {@code null} after the
   *         remote endpoint has half-closed.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  @Nullable
  public abstract RespT pollUninterruptibly(long timeout, TimeUnit unit);

  /**
   * Tries to get a response from the remote endpoint  If the call is completed, or the remote
   * endpoint half-closes, this may return null.  Unlike {@link #poll()}, this method never blocks
   * waiting for a response.
   *
   * @return A response or {@code null} if not available.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  @Nullable
  public abstract RespT pollNow();

  /**
   * Returns a response from the remote endpoint, waiting until one is available.
   *
   * @throws NoSuchElementException if the remote endpoint has half-closed.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   * @throws InterruptedException if interrupted while waiting
   */
  public abstract RespT take() throws InterruptedException;

  /**
   * Returns a response from the remote endpoint, waiting until one is available.  Unlike
   * {@link #take()}, if the calling thread is interrupted, it will be ignored.
   *
   * @throws NoSuchElementException if the remote endpoint has half-closed.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  public abstract RespT takeUninterruptibly();

  /**
   * Tries to get a response from the remote endpoint, until the stub is writable.  If there are
   * messages available, this method will return a message even if the stub is writable.
   *
   * @return A response or {@code null} if not available.  Always returns {@code null} after the
   *         remote endpoint has half-closed.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  @Nullable
  public abstract RespT pollUntilWritable();

  /**
   * Tries to get a response from the remote endpoint, waiting up until the given timeout, or
   * until the stub is writable.  If there are messages available, this method will return a
   * message even if the stub is writable.
   *
   * @param timeout how long to wait before returning, in units of {@code unit}
   * @param unit a {@link TimeUnit} determining how to interpret {@code timeout}.
   * @return A response or {@code null} if not available.  Always returns {@code null} after the
   *         remote endpoint has half-closed.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  @Nullable
  public abstract RespT pollUntilWritable(long timeout, TimeUnit unit);


  /**
   * Tries to get a response from the remote endpoint, until the stub is writable.  If there are
   * messages available, this method will return a message even if the stub is writable.  Unlike
   * {@link #pollUntilWritable()}, if the calling thread is interrupted, it will be ignored.
   *
   * @return A response or {@code null} if not available.  Always returns {@code null} after the
   *         remote endpoint has half-closed.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  @Nullable
  public abstract RespT pollUninterruptiblyUntilWritable();

  /**
   * Tries to get a response from the remote endpoint, waiting up until the given timeout, or
   * until the stub is writable.  If there are messages available, this method will return a
   * message even if the stub is writable.  Unlike
   * {@link #pollUntilWritable(long, TimeUnit)}, if the calling thread is interrupted,
   * it will be ignored.
   *
   * @param timeout how long to wait before returning, in units of {@code unit}
   * @param unit a {@link TimeUnit} determining how to interpret {@code timeout}.
   * @return A response or {@code null} if not available.  Always returns {@code null} after the
   *         remote endpoint has half-closed.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  @Nullable
  public abstract RespT pollUninterruptiblyUntilWritable(long timeout, TimeUnit unit);

  /**
   * Attempts to send a request message, blocking if sending would cause excessive buffering.  This
   * may fail, if the RPC is completed while waited, or the current thread is interrupted.
   *
   * @param req the message to send
   * @return {@code true} if the message was enqueued.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  public abstract boolean offer(ReqT req);

  /**
   * Attempts to send a request message if it would not result in excessive buffering.  Waits up
   * to the given timeout.
   *
   * @param req the message to send
   * @param timeout how long to wait before returning, in units of {@code unit}
   * @param unit a {@link TimeUnit} determining how to interpret {@code timeout}.
   * @return {@code true} if the message was enqueued.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  public abstract boolean offer(ReqT req, long timeout, TimeUnit unit);

  /**
   * Attempts to send a request message if it would not result in excessive buffering.  Unlike
   * {@link #offer(Object)}, if the calling thread is interrupted, it will be ignored.
   *
   * @param req the message to send
   * @return {@code true} if the message was enqueued.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  public abstract boolean offerUninterruptibly(ReqT req);

  /**
   * Attempts to send a request message if it would not result in excessive buffering.  Waits up
   * to the given timeout.  Unlike {@link #offer(Object, long, TimeUnit)}, if the calling thread is
   * interrupted, it will be ignored.
   *
   * @param req the message to send
   * @param timeout how long to wait before returning, in units of {@code unit}
   * @param unit a {@link TimeUnit} determining how to interpret {@code timeout}.
   * @return {@code true} if the message was enqueued.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  public abstract boolean offerUninterruptibly(ReqT req, long timeout, TimeUnit unit);

  /**
   * Attempts to send a request message, blocking until there would not be excessive buffering.
   *
   * @param req the message to send
   * @throws InterruptedException if interrupted while waiting.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  public abstract void put(ReqT req) throws InterruptedException;

  /**
   * Attempts to send a request message, blocking until there would not be excessive buffering.
   * Unlike {@link #put(Object)} if the calling thread is interrupted, it will be
   * ignored.
   *
   * @param req the message to send
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  public abstract void putUninterruptibly(ReqT req);

  /**
   * Attempts to send a request message, buffering the message if it cannot be sent immediately.
   * Users should avoid using this method.
   *
   * @param req the message to send
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  public abstract void putNow(ReqT req);

  /**
   * Attempts to send a request message, blocking until there would not be excessive buffering,
   * or until the stub becomes readable.   If the message can be sent and the stub is readable,
   * the message will always be sent.
   *
   * @param req the message to send
   * @return {@code true} if the message was enqueued.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  public abstract boolean offerUntilReadable(ReqT req);

  /**
   * Attempts to send a request message, blocking until there would not be excessive buffering.
   * Waits up to the given timeout or if the stub becomes readable.   If the message can be sent
   * and the stub is readable, the message will always be sent.
   *
   * @param req the message to send
   * @param timeout how long to wait before returning, in units of {@code unit}
   * @param unit a {@link TimeUnit} determining how to interpret {@code timeout}.
   * @return {@code true} if the message was enqueued.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  public abstract boolean offerUntilReadable(ReqT req, long timeout, TimeUnit unit);

  /**
   * Attempts to send a request message, blocking until there would not be excessive buffering,
   * or until the stub becomes readable.   If the message can be sent and the stub is readable,
   * the message will always be sent.  Unlike
   * {@link #offerUntilReadable(Object)}, if the calling thread is interrupted, it
   * will be ignored.
   *
   * @param req the message to send
   * @return {@code true} if the message was enqueued.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  public abstract boolean offerUninterruptiblyUntilReadable(ReqT req);

  /**
   * Attempts to send a request message, blocking until there would not be excessive buffering.
   * Waits up to the given timeout or if the stub becomes readable.   If the message can be sent
   * and the stub is readable, the message will always be sent.  Unlike
   * {@link #offerUntilReadable(Object, long, TimeUnit)}, if the calling thread is interrupted, it
   * will be ignored.
   *
   * @param req the message to send
   * @param timeout how long to wait before returning, in units of {@code unit}
   * @param unit a {@link TimeUnit} determining how to interpret {@code timeout}.
   * @return {@code true} if the message was enqueued.
   * @throws StatusRuntimeException if the RPC has completed with a non-OK {@link Status}.
   */
  public abstract boolean offerUninterruptiblyUntilReadable(ReqT req, long timeout, TimeUnit unit);

  public abstract boolean isComplete();

  @Override
  public abstract void close();

  /**
   * Starts a call.
   */
  public static <ReqT, RespT> SyncClientCall<ReqT, RespT> call(
      Channel channel, MethodDescriptor<ReqT, RespT> method, CallOptions opts) {
    if (method.getType().clientSendsOneMessage()) {
      // Alternatively, we could call half close for the user, but that would involve state.
      method = method.toBuilder().setType(MethodType.UNKNOWN).build();
    }
    ClientCall<ReqT, RespT> c = channel.newCall(method, opts);
    return SyncClientCall.createAndStart(c, new Metadata());
  }

  public static final class SyncClientCall<ReqT, RespT> extends SyncCall<ReqT, RespT> {

    private final ClientCall<ReqT, RespT> call;
    // @javax.annotation.concurrent.GuardedBy("lock")
    private final Queue<RespT> responses = new ArrayDeque<RespT>();
    // @javax.annotation.concurrent.GuardedBy("lock")
    private Status status;
    // @javax.annotation.concurrent.GuardedBy("lock")
    private Metadata trailers;

    private final Lock lock = new ReentrantLock();
    private final Condition cond = lock.newCondition();

    boolean halfClosed;
    boolean cancelled;

    private SyncClientCall(ClientCall<ReqT, RespT> call) {
      this.call = call;
    }

    static <ReqT, RespT> SyncClientCall<ReqT, RespT> createAndStart(
        ClientCall<ReqT, RespT> call, Metadata md) {
      SyncClientCall<ReqT, RespT> scc = new SyncClientCall<ReqT, RespT>(call);
      scc.call.start(scc.new Listener(), md);
      scc.call.request(1);
      return scc;
    }

    private final class Listener extends ClientCall.Listener<RespT> {

      @Override
      public void onReady() {
        lock.lock();
        try {
          cond.signalAll();
        } finally {
          lock.unlock();
        }
      }

      @Override
      public void onMessage(RespT message) {
        lock.lock();
        try {
          responses.add(message);
          cond.signalAll();
        } finally {
          lock.unlock();
        }
      }

      @Override
      public void onClose(Status status, Metadata trailers) {
        lock.lock();
        try {
          SyncClientCall.this.status = status;
          SyncClientCall.this.trailers = trailers;
          cond.signalAll();
        } finally {
          lock.unlock();
        }
      }
    }

    @Nullable
    @Override
    public RespT poll() {
      return pollInternal(/*interruptible=*/ true, /*checkWrite=*/ false);
    }

    @Nullable
    @Override
    public RespT poll(long timeout, TimeUnit unit) {
      return pollInternal(timeout, unit, /*interruptible=*/ true, /*checkWrite=*/ false);
    }

    @Nullable
    @Override
    public RespT pollUninterruptibly() {
      return pollInternal(/*interruptible=*/ false, /*checkWrite=*/ false);
    }

    @Nullable
    @Override
    public RespT pollUninterruptibly(long timeout, TimeUnit unit) {
      return pollInternal(timeout, unit, /*interruptible=*/ false, /*checkWrite=*/ false);
    }

    @Nullable
    @Override
    public RespT pollNow() {
      lock.lock();
      try {
        checkStatus();
        return responses.poll();
      } finally {
        lock.unlock();
      }
    }

    @Override
    public RespT take() throws InterruptedException {
      RespT response = pollInternal(/*interruptible=*/ true, /*checkWrite=*/ false);
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException();
      } else if (response == null) {
        // Because poll checks that the call is readable and cannot timeout, the only way to reach
        // this is by the status having been set.  This means if there is no response, the only way
        // is if the call is over.  The status must be OK, or else an exception would have been
        // thrown.
        assert status != null && status.isOk();
        throw new NoSuchElementException("call half closed");
      }
      return response;
    }

    @Override
    public RespT takeUninterruptibly() {
      RespT response = pollInternal(/*interruptible=*/ false, /*checkWrite=*/ false);
      if (response == null) {
        // Because poll checks that the call is readable and cannot timeout, the only way to reach
        // this is by the status having been set.  This means if there is no response, the only way
        // is if the call is over.  The status must be OK, or else an exception would have been
        // thrown.
        assert status != null && status.isOk();
        throw new NoSuchElementException("call half closed");
      }
      return response;
    }

    @Override
    @Nullable
    public RespT pollUntilWritable() {
      return pollInternal(/*interruptible=*/ true, /*checkWrite=*/ true);
    }

    @Nullable
    @Override
    public RespT pollUntilWritable(long timeout, TimeUnit unit) {
      return pollInternal(timeout, unit, /*interruptible=*/ true, /*checkWrite=*/ true);
    }

    @Override
    public RespT pollUninterruptiblyUntilWritable() {
      return pollInternal(/*interruptible=*/ false, /*checkWrite=*/ true);
    }

    @Nullable
    @Override
    public RespT pollUninterruptiblyUntilWritable(long timeout, TimeUnit unit) {
      return pollInternal(timeout, unit, /*interruptible=*/ false, /*checkWrite=*/ true);
    }

    @Override
    public boolean offer(ReqT req) {
      return offerInternal(req, /*interruptible=*/ true, /*checkRead=*/ false);
    }

    @Override
    public boolean offer(ReqT req, long timeout, TimeUnit unit) {
      return offerInternal(req, timeout, unit, /*interruptible=*/ true, /*checkRead=*/ false);
    }

    @Override
    public boolean offerUninterruptibly(ReqT req) {
      return offerInternal(req, /*interruptible=*/ false, /*checkRead=*/ false);
    }

    @Override
    public boolean offerUninterruptibly(ReqT req, long timeout, TimeUnit unit) {
      return offerInternal(req, timeout, unit, /*interruptible=*/ false, /*checkRead=*/ false);
    }

    @Override
    public void put(ReqT req) throws InterruptedException {
      boolean queued = offerInternal(req, /*interruptible=*/ true, /*checkRead=*/ false);
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException();
      } else if (!queued) {
        // Because offer checks that the call is writable and cannot timeout, the only way to
        // reach this is by the status having been set.  This means if it could not write, the
        // only way is if the call is over.  The status must be OK, or else an exception would
        // have been thrown.
        assert status != null && status.isOk();
        throw new NoSuchElementException("call completed");
      }
    }

    @Override
    public void putUninterruptibly(ReqT req) {
      boolean queued = offerInternal(req, /*interruptible=*/ false, /*checkRead=*/ false);
      if (!queued) {
        // Because offer checks that the call is writable and cannot timeout, the only way to
        // reach this is by the status having been set.  This means if it could not write, the
        // only way is if the call is over.  The status must be OK, or else an exception would
        // have been thrown.
        assert status != null && status.isOk();
        throw new NoSuchElementException("call completed");
      }
    }

    @Override
    public void putNow(ReqT req) {
      lock.lock();
      try {
        checkStatus();
        call.sendMessage(req);
      } finally {
        lock.unlock();
      }
    }

    @Override
    public boolean offerUntilReadable(ReqT req) {
      return offerInternal(req, /*interruptible=*/ true, /*checkRead=*/ true);
    }

    @Override
    public boolean offerUntilReadable(ReqT req, long timeout, TimeUnit unit) {
      return offerInternal(req, timeout, unit, /*interruptible=*/ true, /*checkRead=*/ true);
    }

    @Override
    public boolean offerUninterruptiblyUntilReadable(ReqT req) {
      return offerInternal(req, /*interruptible=*/ false, /*checkRead=*/ true);
    }

    @Override
    public boolean offerUninterruptiblyUntilReadable(ReqT req, long timeout, TimeUnit unit) {
      return offerInternal(req, timeout, unit, /*interruptible=*/ false, /*checkRead=*/ true);
    }

    /**
     *
     */
    @Override
    public void close() {
      if (!halfClosed) {
        halfClose();
      }
      if (!cancelled) {
        cancel("Cancelled by close()", null);
      }
    }

    /**
     * Indicates the RPC is done sending.
     */
    public void halfClose() {
      halfClosed = true;
      call.halfClose();
    }

    /**
     * Cancels the RPC.
     */
    public void cancel(@Nullable String message, @Nullable Throwable t) {
      cancelled = true;
      call.cancel(message, t);
    }

    @Override
    public boolean isComplete() {
      lock.lock();
      try {
        return status != null;
      } finally {
        lock.unlock();
      }
    }

    /**
     * Get's the status of the RPC.
     *
     * @return {@code null} if incomplete, else the status.
     */
    @Nullable
    public Status getStatus() {
      lock.lock();
      try {
        return status;
      } finally {
        lock.unlock();
      }
    }

    private RespT pollInternal(
        long timeout,
        TimeUnit unit,
        boolean interruptible,
        boolean checkWrite) {
      long remainingNanos = unit.toNanos(timeout);
      long end = System.nanoTime() + remainingNanos;
      RespT response;
      boolean interrupted = false;
      lock.lock();
      try {
        while (true) {
          if (!responses.isEmpty() || status != null) {
            break;
          } else if (checkWrite && call.isReady()) {
            break;
          }
          try {
            if (!cond.await(remainingNanos, TimeUnit.NANOSECONDS)) {
              break;
            }
          } catch (InterruptedException e) {
            if (interruptible) {
              Thread.currentThread().interrupt();
              return null;
            }
            interrupted = true;
          }
          remainingNanos = end - System.nanoTime();
        }
        if ((response = responses.poll()) == null) {
          checkStatus();
        } else {
          call.request(1);
        }
        return response;
      } finally {
        lock.unlock();
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }

    private RespT pollInternal(boolean interruptible, boolean checkWrite) {
      RespT response;
      lock.lock();
      try {
        while (true) {
          if (!responses.isEmpty() || status != null) {
            break;
          } else if (checkWrite && call.isReady()) {
            break;
          }
          if (interruptible) {
            try {
              cond.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return null;
            }
          } else {
            cond.awaitUninterruptibly();
          }
        }
        if ((response = responses.poll()) == null) {
          checkStatus();
        } else {
          call.request(1);
        }
        return response;
      } finally {
        lock.unlock();
      }
    }

    private boolean offerInternal(ReqT req, boolean interruptible, boolean checkRead) {
      boolean isReady;
      lock.lock();
      try {
        while (true) {
          isReady = call.isReady();
          if (isReady || status != null) {
            break;
          } else if (checkRead && (!responses.isEmpty() || status != null)) {
            break;
          }
          if (interruptible) {
            try {
              cond.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return false;
            }
          } else {
            cond.awaitUninterruptibly();
          }
        }
        checkStatus();
        if (isReady) {
          assert status == null;
          call.sendMessage(req);
          return true;
        }
        return false;
      } finally {
        lock.unlock();
      }
    }

    private boolean offerInternal(
        ReqT req, long timeout, TimeUnit unit, boolean interruptible, boolean checkRead) {
      long remainingNanos = unit.toNanos(timeout);
      long end = System.nanoTime() + remainingNanos;
      boolean interrupted = false;
      boolean isReady;
      lock.lock();
      try {
        while (true) {
          isReady = call.isReady();
          if (isReady || status != null) {
            break;
          } else if (checkRead && (!responses.isEmpty() || status != null)) {
            break;
          }
          try {
            if (!cond.await(remainingNanos, TimeUnit.NANOSECONDS)) {
              return false;
            }
          } catch (InterruptedException e) {
            if (interruptible) {
              Thread.currentThread().interrupt();
              return false;
            }
            interrupted = true;
          }
          remainingNanos = end - System.nanoTime();
        }
        checkStatus();
        if (isReady) {
          assert status == null;
          call.sendMessage(req);
          return true;
        }
        return false;
      } finally {
        lock.unlock();
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }

    private void checkStatus() {
      if (status != null && !status.isOk()) {
        throw status.asRuntimeException(trailers);
      }
    }
  }
}
