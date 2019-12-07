/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2019 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.java.logging;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;

import java.util.Collection;

/**
 * A special-purpose {@link Thread} that monitors a {@link
 * java.util.logging.LogManager}'s configuration file for changes.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see LogManager#readConfiguration()
 *
 * @see WatchService
 */
public class LoggingConfigFileMonitor extends Thread {


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link LoggingConfigFileMonitor}.
   *
   * <p>The {@link Thread#setDaemon(boolean)} method is invoked with a
   * value of {@code true} as a part of any invocation of this
   * constructor.</p>
   *
   * <p>The {@link Thread#setPriority(int)} method is invoked with the
   * value of the {@link Thread#MIN_PRIORITY} field as a part of any
   * invocation of this constructor.</p>
   */
  public LoggingConfigFileMonitor() {
    super();
    this.setDaemon(true);
    this.setPriority(Thread.MIN_PRIORITY);
  }


  /*
   * Instance methods.
   */


  /**
   * Monitors the {@link java.util.logging.LogManager} configuration
   * file for creation, modification or deletion, and, upon the
   * observation of such a change, calls the {@link
   * #readConfiguration()} method.
   *
   * @see #readConfiguration()
   *
   * @see java.util.logging.LogManager
   */
  @Override
  public final void run() {
    Path loggingConfigFile = Paths.get(System.getProperty("java.util.logging.config.file"));
    if (loggingConfigFile == null) {
      loggingConfigFile =
        Paths.get(System.getProperty("java.home"),
                  "conf",
                  "logging.properties")
        .toAbsolutePath()
        .normalize();
    }
    final Path directory = loggingConfigFile.getParent();
    try (final WatchService watcher = FileSystems.getDefault().newWatchService()) {
      if (watcher != null) {
        WatchKey watchKey = directory.register(watcher,
                                               StandardWatchEventKinds.ENTRY_MODIFY,
                                               StandardWatchEventKinds.ENTRY_CREATE,
                                               StandardWatchEventKinds.ENTRY_DELETE);
        while (watchKey != null && watchKey.isValid()) {
          final Collection<? extends WatchEvent<?>> events = watchKey.pollEvents();
          if (events != null && !events.isEmpty()) {
            for (final WatchEvent<?> event : events) {
              if (isLoggingConfigFileEvent(loggingConfigFile, event)) {
                try {
                  this.readConfiguration();
                } catch (final IOException | RuntimeException exception) {
                  exception.printStackTrace();
                }
                break;
              }
            }
          }
          if (watchKey.reset()) {
            watchKey = watcher.take();
          }
        }
      }
    } catch (final InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
    } catch (final IOException ioException) {
      ioException.printStackTrace();
    }
  }

  /**
   * Reloads and processes any new {@link
   * java.util.logging.LogManager} configuration {@linkplain
   * java.util.logging.LogManager following the rules described in the
   * <code>java.util.logging.LogManager</code> documentation}.
   *
   * <p>This implementation calls {@link
   * java.util.logging.LogManager#readConfiguration()}.</p>
   *
   * @exception IOException if there was an error reloading the
   * configuration
   *
   * @see java.util.logging.LogManager#readConfiguration()
   */
  protected void readConfiguration() throws IOException {
    LogManager.getLogManager().readConfiguration();
  }

  private static final boolean isLoggingConfigFileEvent(final Path loggingConfigFile, final WatchEvent<?> event) {
    return
      event != null &&
      !StandardWatchEventKinds.OVERFLOW.equals(event.kind()) &&
      loggingConfigFile.getParent().relativize(loggingConfigFile).equals(event.context());
  }

}
