package com.example;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.infinispan.server.security.Common.sync;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.infinispan.client.rest.RestResponse;
import org.infinispan.client.rest.configuration.RestClientConfigurationBuilder;
import org.infinispan.client.rest.impl.okhttp.RestClientOkHttp;
import org.infinispan.test.Exceptions;

import io.netty.handler.codec.http.HttpResponseStatus;

public class InfinispanServer implements Server {

   private final static Logger LOGGER = Logger.getLogger(InfinispanServer.class.getName());
   private String configuration;
   private String args;

   private static String serverHome;
   private static String serverLog;
   private static String serverLogDir;
   private static String serverScript;
   private static String serverLib;

   private static final String START_PATTERN = "started in";
   private static final String STOP_PATTERN = "Infinispan Server stopped";

   public InfinispanServer(String serverHome) {
      this.serverHome = serverHome;
      this.serverScript = serverHome + "/bin/server.sh";
      this.serverLogDir = serverHome + "/server/log";
      this.serverLog = serverLogDir + "/server.log";
      this.serverLib = serverHome + "/server/lib";
      cleanServerLog();
   }

   @Override
   public boolean start() {
      Process process = null;
      boolean isServerStarted = false;
      ProcessBuilder pb = new ProcessBuilder();
      List<String> commands = new ArrayList<>();
      commands.add(serverScript);
      if(configuration != null) {
         commands.add("-c");
         commands.add(this.configuration);
      }
      if(args != null) {
         commands.add(args);
      }
      pb.command(commands);
      try {
         process = pb.start();
         isServerStarted = runWithTimeout(this::checkServerLog, START_PATTERN);
         if(!isServerStarted) {
            throw new IllegalStateException("The server couldn't start");
         }
      } catch (Exception e) {
         LOGGER.severe(e.getMessage());
      } finally {
         if(process != null)
            process.destroy();
      }

      return isServerStarted;
   }

   //Run with 1 minute timeout
   private boolean runWithTimeout(Function<String, Boolean> function, String logPattern) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      Callable<Boolean> task = () -> function.apply(logPattern);
      Future<Boolean> future = executor.submit(task);
      return Exceptions.unchecked(() -> future.get(1, TimeUnit.MINUTES));
   }

   private boolean checkServerLog(String pattern) {
      return Exceptions.unchecked(() -> {
         Process process = Runtime.getRuntime().exec(String.format("tail -f %s", serverLog));
         try (Stream<String> lines = new BufferedReader(
               new InputStreamReader(process.getInputStream())).lines()) {
            return lines.peek(System.out::println).anyMatch(line -> line.contains(pattern));
         }
      });
   }

   @Override
   public boolean stop(boolean stopCluster) {
      RestResponse response;
      RestClientOkHttp restClient = new RestClientOkHttp(new RestClientConfigurationBuilder().build());
      if(stopCluster) {
         response = sync(restClient.cluster().stop());
      } else {
         response = sync(restClient.server().stop());
      }

      HttpResponseStatus status = HttpResponseStatus.valueOf(response.getStatus());
      boolean isServerStopped = checkServerLog(STOP_PATTERN);
      if (!isServerStopped || status != HttpResponseStatus.OK)
         throw new IllegalStateException("Server not stopped");

      return true;
   }

   @Override
   public boolean restart() {
      boolean stop = stop(true);
      boolean start = start();
      return stop && start;
   }

   @Override
   public boolean isRunning() {
      return false;
   }

   private void cleanServerLog() {
      Exceptions.unchecked(() -> {
         Files.deleteIfExists(Paths.get(serverLog));
         boolean isServerLogDirectoryExist = Files.exists(Paths.get(serverLogDir));
         if(!isServerLogDirectoryExist)
            Files.createDirectory(Paths.get(serverLogDir));
         Files.createFile(Paths.get(serverLog));
      });
   }

   public InfinispanServer configuration(String configuration) {
      this.configuration = configuration;
      return this;
   }

   public InfinispanServer args(String args) {
      this.args = args;
      return this;
   }

   public InfinispanServer copyLibToServer(String lib) {
      Path source = Paths.get(lib);
      Path target = Paths.get(String.format("%s/%s", serverLib, source.getFileName()));
      Exceptions.unchecked(() -> Files.copy(source, target, REPLACE_EXISTING, COPY_ATTRIBUTES));
      return this;
   }
}
