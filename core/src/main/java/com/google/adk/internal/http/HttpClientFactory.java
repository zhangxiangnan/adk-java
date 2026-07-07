/*
 * Copyright 2026 Google LLC
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

package com.google.adk.internal.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

/** Utility class for common HTTP client configuration across the ADK. */
public final class HttpClientFactory {

  private static volatile ExecutorService customExecutorService = null;
  private static final Map<String, OkHttpClient> sharedClients = new ConcurrentHashMap<>();

  private HttpClientFactory() {}

  /**
   * Sets a custom {@link ExecutorService} to be used by the shared HTTP client dispatchers. This is
   * useful in managed environments like Boq where thread construction must be managed by the
   * container.
   */
  public static void setExecutorService(ExecutorService executorService) {
    customExecutorService = executorService;
    sharedClients.clear();
  }

  private static ThreadFactory createDaemonThreadFactory(String name) {
    return r -> {
      Thread t = new Thread(r, name + "-Dispatcher");
      t.setDaemon(true);
      return t;
    };
  }

  private static Dispatcher createDaemonDispatcher(String name) {
    ExecutorService executor = customExecutorService;
    if (executor == null) {
      ThreadFactory daemonThreadFactory = createDaemonThreadFactory(name);
      executor =
          new ThreadPoolExecutor(
              0,
              Integer.MAX_VALUE,
              60L,
              TimeUnit.SECONDS,
              new SynchronousQueue<Runnable>(),
              daemonThreadFactory);
    }
    return new Dispatcher(executor);
  }

  private static OkHttpClient buildSharedHttpClient(String threadName) {
    return new OkHttpClient.Builder().dispatcher(createDaemonDispatcher(threadName)).build();
  }

  /**
   * Returns a shared OkHttpClient instance equipped with a daemon thread dispatcher or custom
   * injected executor. Repeated calls with the same name reuse the cached shared client.
   *
   * @param threadName The prefix name to use for the dispatcher threads.
   * @return A pre-configured OkHttpClient.
   */
  public static OkHttpClient createSharedHttpClient(String threadName) {
    return sharedClients.computeIfAbsent(threadName, HttpClientFactory::buildSharedHttpClient);
  }
}
