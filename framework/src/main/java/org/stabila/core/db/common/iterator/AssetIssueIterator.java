package org.stabila.core.db.common.iterator;

import java.util.Iterator;
import java.util.Map.Entry;
import org.stabila.core.capsule.AssetIssueCapsule;

public class AssetIssueIterator extends AbstractIterator<AssetIssueCapsule> {

  public AssetIssueIterator(Iterator<Entry<byte[], byte[]>> iterator) {
    super(iterator);
  }

  @Override
  protected AssetIssueCapsule of(byte[] value) {
    return new AssetIssueCapsule(value);
  }
}