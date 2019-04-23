/*
 * Copyright 2019 The gRPC Authors
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

import io.grpc.internal.ThreadLocalCache.Slots.CachedWeakRef;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

/**
 * A thread local weak-ref cache.
 */
public final class ThreadLocalCache {
  private static final ThreadLocal<Slots> slots = new ThreadLocal<>();

  private ThreadLocalCache() {}

  /**
   * Gets a cached object from this thread, or {@code null} if there is none.
   */
  @Nullable
  public static Object get(int slotId) {
    Slots localSlots = slots.get();
    if (localSlots == null) {
      return null;
    }
    return localSlots.get(slotId);
  }

  /**
   * Sets an object into this thread cache.
   */
  public static void set(int slotId, @Nullable Object value) {
    Slots localSlots = slots.get();
    if (localSlots == null) {
      slots.set(localSlots = new Slots());
    }
    localSlots.set(slotId, new CachedWeakRef(value));
  }

  /**
   * Sets an object into this thread cache, but without weakly referring to it.
   */
  public static void setStrong(int slotId, @Nullable Object value) {
    Slots localSlots = slots.get();
    if (localSlots == null) {
      slots.set(localSlots = new Slots());
    }
    localSlots.set(slotId, value);
  }

  /**
   * Returns a slot id for use with {@link #get} and {@link #set}.
   */
  public static int getSlotId() {
    return Slots.slotAllocator.getAndIncrement();
  }

  static final class Slots {
    static final AtomicInteger slotAllocator = new AtomicInteger();
    private Object[] slots = new Object[0];

    static final class CachedWeakRef extends WeakReference<Object> {

      CachedWeakRef(Object referent) {
        super(referent);
      }
    }

    @Nullable
    Object get(int slotId) {
      if (slotId >= slots.length) {
        return null;
      }
      Object value = slots[slotId];
      if (value instanceof CachedWeakRef) {
        if ((value = ((CachedWeakRef) value).get()) == null) {
          slots[slotId] = null;
        }
      }
      return value;
    }

    void set(int slotId, @Nullable Object value) {
      if (slots.length >= slotId) {
        slots = Arrays.copyOf(slots, slotId + 1);
      } else {
        Object oldValue = slots[slotId];
        if (oldValue instanceof CachedWeakRef) {
          ((CachedWeakRef) oldValue).clear();
        }
      }
      slots[slotId] = value;
    }
  }
}
