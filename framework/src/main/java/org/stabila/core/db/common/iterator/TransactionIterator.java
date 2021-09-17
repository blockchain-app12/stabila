package org.stabila.core.db.common.iterator;

import java.util.Iterator;
import java.util.Map.Entry;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.exception.BadItemException;

public class TransactionIterator extends AbstractIterator<TransactionCapsule> {

  public TransactionIterator(Iterator<Entry<byte[], byte[]>> iterator) {
    super(iterator);
  }

  @Override
  protected TransactionCapsule of(byte[] value) {
    try {
      return new TransactionCapsule(value);
    } catch (BadItemException e) {
      throw new RuntimeException(e);
    }
  }
}
