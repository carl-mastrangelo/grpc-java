package io.grpc;

/**
 * A linear map of keys and values.   Optimized for very small sizes.
 */
final class KeyValues {

  private static final Object[] EMTPY = new Object[0];

  private final Object[] kvs;

  KeyValues(Object[] kvs) {
    this.kvs = kvs;
  }

  KeyValues() {
    this(EMTPY);
  }

  KeyValues put(Object k, Object v) {
    Object[] newkvs = new Object[kvs.length + 2];
    newkvs[0] = k;
    newkvs[1] = v;
    System.arraycopy(kvs, 0, newkvs, 2, kvs.length);
    return new KeyValues(newkvs);
  }

  Object get(Object k) {
    for (int i = 0; i < kvs.length; i += 2) {
      if (kvs[i] == k) {
        return kvs[i+1];
      }
    }
    return null;
  }
}
