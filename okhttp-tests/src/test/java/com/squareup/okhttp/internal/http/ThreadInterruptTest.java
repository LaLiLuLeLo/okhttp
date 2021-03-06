/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.fail;

public final class ThreadInterruptTest {
  private final MockWebServer server = new MockWebServer();
  private final OkHttpClient client = new OkHttpClient();

  @Test public void interruptWritingRequestBody() throws Exception {
    int requestBodySize = 2 * 1024 * 1024; // 2 MiB

    server.enqueue(new MockResponse()
        .throttleBody(64 * 1024, 125, TimeUnit.MILLISECONDS)); // 500 Kbps
    server.play();

    interruptLater(500);

    HttpURLConnection connection = new OkUrlFactory(client).open(server.getUrl("/"));
    connection.setDoOutput(true);
    connection.setFixedLengthStreamingMode(requestBodySize);
    OutputStream requestBody = connection.getOutputStream();
    byte[] buffer = new byte[1024];
    try {
      for (int i = 0; i < requestBodySize; i += buffer.length) {
        requestBody.write(buffer);
        requestBody.flush();
      }
      fail("Expected thread to be interrupted");
    } catch (InterruptedIOException expected) {
    }

    connection.disconnect();
  }

  @Test public void interruptReadingResponseBody() throws Exception {
    int responseBodySize = 2 * 1024 * 1024; // 2 MiB

    server.enqueue(new MockResponse()
        .setBody(new byte[responseBodySize])
        .throttleBody(64 * 1024, 125, TimeUnit.MILLISECONDS)); // 500 Kbps
    server.play();

    interruptLater(500);

    HttpURLConnection connection = new OkUrlFactory(client).open(server.getUrl("/"));
    InputStream responseBody = connection.getInputStream();
    byte[] buffer = new byte[1024];
    try {
      while (responseBody.read(buffer) != -1) {
      }
      fail("Expected thread to be interrupted");
    } catch (InterruptedIOException expected) {
    }

    connection.disconnect();
  }

  private void interruptLater(final int delayMillis) {
    final Thread toInterrupt = Thread.currentThread();
    Thread interruptingCow = new Thread() {
      @Override public void run() {
        try {
          sleep(delayMillis);
          toInterrupt.interrupt();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    };
    interruptingCow.start();
  }
}
