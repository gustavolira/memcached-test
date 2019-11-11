package com.example;

import java.io.IOException;

public interface Server {

   boolean start() throws IOException;
   boolean stop(boolean stopCluster) throws IOException;
   boolean restart();
   boolean isRunning();
}

