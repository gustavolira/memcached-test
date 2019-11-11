package com.example;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;

import net.spy.memcached.MemcachedClient;

public class MemcachedTest {
   Logger LOGGER = Logger.getLogger(MemcachedTest.class.getName());

   @Test
   public void testStartStop() throws IOException {
      String serverHome = System.getProperty("SERVER_HOME");
      InfinispanServer infinispanServer = new InfinispanServer(serverHome);

      infinispanServer.start();
      InetSocketAddress inetSocketAddress = new InetSocketAddress(InetAddress.getByName("localhost"), 11221);
      MemcachedClient memcachedClient = new MemcachedClient(inetSocketAddress);

      memcachedClient.set("k1", 0, "v1");
      Assert.assertNotNull(memcachedClient.get("k1"));
      Assert.assertEquals(memcachedClient.get("k1"), "v1");

      LOGGER.info("STOPPING THE SERVER");
      infinispanServer.stop(false);
      LOGGER.info("STARTING THE SERVER AGAIN");
      infinispanServer.start();

      memcachedClient = new MemcachedClient(inetSocketAddress);

      Assert.assertNotNull(memcachedClient.get("k1"));
      Assert.assertEquals(memcachedClient.get("k1"), "v1");

      infinispanServer.stop(false);

   }
}
