/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.initialization;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import com.metamx.common.lifecycle.LifecycleStart;
import com.metamx.common.lifecycle.LifecycleStop;
import com.metamx.common.logger.Logger;
import io.druid.common.config.Log4jShutdown;
import io.druid.guice.ManageLifecycle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;

import java.lang.reflect.Field;

public class Log4jShutterDownerModule implements Module
{
  private static final Logger log = new Logger(Log4jShutterDownerModule.class);

  @Override
  public void configure(Binder binder)
  {
    // Instantiate eagerly so that we get everything registered and put into the Lifecycle
    // This makes the shutdown run pretty darn near last.
    binder.bind(Key.get(Log4jShutterDowner.class, Names.named("ForTheEagerness")))
          .to(Log4jShutterDowner.class)
          .asEagerSingleton();
  }


  @ManageLifecycle
  @Provides
  public Log4jShutterDowner getShutterDowner()
  {
    return new Log4jShutterDowner();
  }

  public static class Log4jShutterDowner
  {
    @LifecycleStart
    public void start()
    {
      log.debug("Log4j shutter downer is waiting");
    }

    @LifecycleStop
    public void stop()
    {
      try {
        // Reflection to try and allow non Log4j2 stuff to run. This acts as a gateway to stop errors in the next few lines
        final Class<?> logManagerClazz = Class.forName("org.apache.logging.log4j.LogManager");
        // Cleanup logs
        final Field shutdownCallbackRegistryField = Log4jContextFactory.class.getDeclaredField(
            "shutdownCallbackRegistry"
        );
        shutdownCallbackRegistryField.setAccessible(true);
        final Log4jShutdown log4jShutdown = (Log4jShutdown) shutdownCallbackRegistryField.get(LogManager.getFactory());
        log.info("Shutting down log4j callbacks");
        log4jShutdown.runCallbacks();
      }
      catch (ClassNotFoundException | ClassCastException | NoSuchFieldException | IllegalAccessException e) {
        log.warn(e, "Not attempting log shutdown");
      }
    }
  }
}
