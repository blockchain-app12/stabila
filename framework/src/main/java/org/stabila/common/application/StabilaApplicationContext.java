package org.stabila.common.application;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.stabila.common.overlay.discover.DiscoverServer;
import org.stabila.common.overlay.discover.node.NodeManager;
import org.stabila.common.overlay.server.ChannelManager;
import org.stabila.core.db.Manager;

public class StabilaApplicationContext extends AnnotationConfigApplicationContext {

  public StabilaApplicationContext() {
  }

  public StabilaApplicationContext(DefaultListableBeanFactory beanFactory) {
    super(beanFactory);
  }

  public StabilaApplicationContext(Class<?>... annotatedClasses) {
    super(annotatedClasses);
  }

  public StabilaApplicationContext(String... basePackages) {
    super(basePackages);
  }

  @Override
  public void destroy() {

    Application appT = ApplicationFactory.create(this);
    appT.shutdownServices();
    appT.shutdown();

    DiscoverServer discoverServer = getBean(DiscoverServer.class);
    discoverServer.close();
    ChannelManager channelManager = getBean(ChannelManager.class);
    channelManager.close();
    NodeManager nodeManager = getBean(NodeManager.class);
    nodeManager.close();

    Manager dbManager = getBean(Manager.class);
    dbManager.stopRePushThread();
    dbManager.stopRePushTriggerThread();
    super.destroy();
  }
}
