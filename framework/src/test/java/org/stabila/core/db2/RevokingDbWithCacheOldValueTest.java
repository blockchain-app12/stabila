package org.stabila.core.db2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.stabila.core.Constant;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.AbstractRevokingStore;
import org.stabila.core.db.RevokingDatabase;
import org.stabila.core.db.StabilaStoreWithRevoking;
import org.stabila.core.exception.RevokingStoreIllegalStateException;
import org.stabila.common.application.Application;
import org.stabila.common.application.ApplicationFactory;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.utils.FileUtil;
import org.stabila.core.db2.SnapshotRootTest.ProtoCapsuleTest;

@Slf4j
public class RevokingDbWithCacheOldValueTest {

  private AbstractRevokingStore revokingDatabase;
  private StabilaApplicationContext context;
  private Application appT;

  @Before
  public void init() {
    Args.setParam(new String[]{"-d", "output_revokingStore_test"}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    appT = ApplicationFactory.create(context);
    revokingDatabase = new TestRevokingStabilaDatabase();
    revokingDatabase.enable();
  }

  @After
  public void removeDb() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File("output_revokingStore_test"));
  }

  @Test
  public synchronized void testReset() {
    revokingDatabase.getStack().clear();
    TestRevokingStabilaStore stabilaDatabase = new TestRevokingStabilaStore(
        "testrevokingstabilastore-testReset", revokingDatabase);
    ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("reset").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      stabilaDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
      tmpSession.commit();
    }
    Assert.assertTrue(stabilaDatabase.has(testProtoCapsule.getData()));
    stabilaDatabase.reset();
    Assert.assertFalse(stabilaDatabase.has(testProtoCapsule.getData()));
    stabilaDatabase.reset();
  }

  @Test
  public synchronized void testPop() throws RevokingStoreIllegalStateException {
    revokingDatabase.getStack().clear();
    TestRevokingStabilaStore stabilaDatabase = new TestRevokingStabilaStore(
        "testrevokingstabilastore-testPop", revokingDatabase);

    for (int i = 1; i < 11; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("pop" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        stabilaDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        Assert.assertEquals(1, revokingDatabase.getActiveDialog());
        tmpSession.commit();
        Assert.assertEquals(i, revokingDatabase.getStack().size());
        Assert.assertEquals(0, revokingDatabase.getActiveDialog());
      }
    }

    for (int i = 1; i < 11; i++) {
      revokingDatabase.pop();
      Assert.assertEquals(10 - i, revokingDatabase.getStack().size());
    }

    stabilaDatabase.close();

    Assert.assertEquals(0, revokingDatabase.getStack().size());
  }

  @Test
  public synchronized void testUndo() throws RevokingStoreIllegalStateException {
    revokingDatabase.getStack().clear();
    TestRevokingStabilaStore stabilaDatabase = new TestRevokingStabilaStore(
        "testrevokingstabilastore-testUndo", revokingDatabase);

    ISession dialog = revokingDatabase.buildSession();
    for (int i = 0; i < 10; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("undo" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        stabilaDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        Assert.assertEquals(2, revokingDatabase.getStack().size());
        tmpSession.merge();
        Assert.assertEquals(1, revokingDatabase.getStack().size());
      }
    }

    Assert.assertEquals(1, revokingDatabase.getStack().size());

    dialog.destroy();
    Assert.assertTrue(revokingDatabase.getStack().isEmpty());
    Assert.assertEquals(0, revokingDatabase.getActiveDialog());

    dialog = revokingDatabase.buildSession();
    revokingDatabase.disable();
    ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest("del".getBytes());
    stabilaDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
    revokingDatabase.enable();

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      stabilaDatabase.put(testProtoCapsule.getData(), new ProtoCapsuleTest("del2".getBytes()));
      tmpSession.merge();
    }

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      stabilaDatabase.put(testProtoCapsule.getData(), new ProtoCapsuleTest("del22".getBytes()));
      tmpSession.merge();
    }

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      stabilaDatabase.put(testProtoCapsule.getData(), new ProtoCapsuleTest("del222".getBytes()));
      tmpSession.merge();
    }

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      stabilaDatabase.delete(testProtoCapsule.getData());
      tmpSession.merge();
    }

    dialog.destroy();

    logger.info(
        "**********testProtoCapsule:" + stabilaDatabase.getUnchecked(testProtoCapsule.getData())
            .toString());
    Assert.assertArrayEquals("del".getBytes(),
        stabilaDatabase.getUnchecked(testProtoCapsule.getData()).getData());
    Assert.assertEquals(testProtoCapsule, stabilaDatabase.getUnchecked(testProtoCapsule.getData()));

    stabilaDatabase.close();
  }

  @Test
  public synchronized void testGetlatestValues() {
    revokingDatabase.getStack().clear();
    TestRevokingStabilaStore stabilaDatabase = new TestRevokingStabilaStore(
        "testrevokingstabilastore-testGetlatestValues", revokingDatabase);

    for (int i = 0; i < 10; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("getLastestValues" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        stabilaDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        tmpSession.commit();
      }
    }
    Set<ProtoCapsuleTest> result = stabilaDatabase.getRevokingDB().getlatestValues(5).stream()
        .map(ProtoCapsuleTest::new)
        .collect(Collectors.toSet());

    for (int i = 9; i >= 5; i--) {
      Assert.assertTrue(result.contains(new ProtoCapsuleTest(("getLastestValues" + i).getBytes())));
    }
    stabilaDatabase.close();
  }

  @Test
  public synchronized void testGetValuesNext() {
    revokingDatabase.getStack().clear();
    TestRevokingStabilaStore stabilaDatabase = new TestRevokingStabilaStore(
        "testrevokingstabilastore-testGetValuesNext", revokingDatabase);

    for (int i = 0; i < 10; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("getValuesNext" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        stabilaDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        tmpSession.commit();
      }
    }
    Set<ProtoCapsuleTest> result =
        stabilaDatabase.getRevokingDB().getValuesNext(
            new ProtoCapsuleTest("getValuesNext2".getBytes()).getData(), 3)
            .stream()
            .map(ProtoCapsuleTest::new)
            .collect(Collectors.toSet());

    for (int i = 2; i < 5; i++) {
      Assert.assertTrue(result.contains(new ProtoCapsuleTest(("getValuesNext" + i).getBytes())));
    }
    stabilaDatabase.close();
  }

  @Test
  public synchronized void testGetKeysNext() {
    revokingDatabase.getStack().clear();
    TestRevokingStabilaStore stabilaDatabase = new TestRevokingStabilaStore(
        "testrevokingstabilastore-testGetKeysNext", revokingDatabase);

    String protoCapsuleStr = "getKeysNext";
    for (int i = 0; i < 10; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest((protoCapsuleStr + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        stabilaDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        tmpSession.commit();
      }
    }

    int start = 2;
    List<byte[]> result =
        stabilaDatabase.getRevokingDB().getKeysNext(
            new ProtoCapsuleTest((protoCapsuleStr + start).getBytes()).getData(), 3);

    for (int i = start; i < 5; i++) {
      Assert.assertArrayEquals(new ProtoCapsuleTest((protoCapsuleStr + i).getBytes()).getData(),
          result.get(i - 2));
    }
    stabilaDatabase.close();
  }

  @Test
  public void shutdown() throws RevokingStoreIllegalStateException {
    revokingDatabase.getStack().clear();
    TestRevokingStabilaStore stabilaDatabase = new TestRevokingStabilaStore(
        "testrevokingstabilastore-shutdown", revokingDatabase);

    List<ProtoCapsuleTest> capsules = new ArrayList<>();
    for (int i = 1; i < 11; i++) {
      revokingDatabase.buildSession();
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("test" + i).getBytes());
      capsules.add(testProtoCapsule);
      stabilaDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
      Assert.assertEquals(revokingDatabase.getActiveDialog(), i);
      Assert.assertEquals(revokingDatabase.getStack().size(), i);
    }

    for (ProtoCapsuleTest capsule : capsules) {
      logger.info(new String(capsule.getData()));
      Assert.assertEquals(capsule, stabilaDatabase.getUnchecked(capsule.getData()));
    }

    revokingDatabase.shutdown();

    for (ProtoCapsuleTest capsule : capsules) {
      logger.info(stabilaDatabase.getUnchecked(capsule.getData()).toString());
      Assert.assertEquals(null, stabilaDatabase.getUnchecked(capsule.getData()).getData());
    }

    Assert.assertEquals(0, revokingDatabase.getStack().size());
    stabilaDatabase.close();

  }

  private static class TestRevokingStabilaStore extends StabilaStoreWithRevoking<ProtoCapsuleTest> {

    protected TestRevokingStabilaStore(String dbName, RevokingDatabase revokingDatabase) {
      super(dbName, revokingDatabase);
    }

    @Override
    public ProtoCapsuleTest get(byte[] key) {
      byte[] value = this.revokingDB.getUnchecked(key);
      return ArrayUtils.isEmpty(value) ? null : new ProtoCapsuleTest(value);
    }
  }

  private static class TestRevokingStabilaDatabase extends AbstractRevokingStore {

  }
}
