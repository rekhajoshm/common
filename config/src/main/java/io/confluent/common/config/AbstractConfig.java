/**
 * Copyright 2015 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

/**
 * Original license:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.confluent.common.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.confluent.common.Configurable;
import io.confluent.common.utils.Utils;

/**
 * A convenient base class for configurations to extend. <p> This class holds both the original
 * configuration that was provided as well as the parsed
 */
public class AbstractConfig {

  private final Logger log = LoggerFactory.getLogger(getClass());

  /* configs for which values have been requested, used to detect unused configs */
  private final Set<String> used;

  /* the original values passed in by the user */
  private final Map<String, ?> originals;

  /* the parsed values */
  private final Map<String, Object> values;

  @SuppressWarnings("unchecked")
  public AbstractConfig(ConfigDef definition, Map<?, ?> originals) {
        /* check that all the keys are really strings */
    for (Object key : originals.keySet()) {
      if (!(key instanceof String)) {
        throw new ConfigException(key.toString(), originals.get(key), "Key must be a string.");
      }
    }
    this.originals = (Map<String, ?>) originals;
    this.values = definition.parse(this.originals);
    this.used = Collections.synchronizedSet(new HashSet<String>());
    logAll();
  }

  protected Object get(String key) {
    if (!values.containsKey(key)) {
      throw new ConfigException(String.format("Unknown configuration '%s'", key));
    }
    used.add(key);
    return values.get(key);
  }

  public int getInt(String key) {
    return (Integer) get(key);
  }

  public long getLong(String key) {
    return (Long) get(key);
  }

  public double getDouble(String key) {
    return (Double) get(key);
  }

  @SuppressWarnings("unchecked")
  public List<String> getList(String key) {
    return (List<String>) get(key);
  }

  public boolean getBoolean(String key) {
    return (Boolean) get(key);
  }

  public String getString(String key) {
    return (String) get(key);
  }

  public Class<?> getClass(String key) {
    return (Class<?>) get(key);
  }

  public Set<String> unused() {
    Set<String> keys = new HashSet<String>(originals.keySet());
    keys.removeAll(used);
    return keys;
  }

  private void logAll() {
    StringBuilder b = new StringBuilder();
    b.append(getClass().getSimpleName());
    b.append(" values: ");
    b.append(Utils.NL);
    for (Map.Entry<String, Object> entry : this.values.entrySet()) {
      b.append('\t');
      b.append(entry.getKey());
      b.append(" = ");
      b.append(entry.getValue());
      b.append(Utils.NL);
    }
    log.info(b.toString());
  }

  /**
   * Log warnings for any unused configurations
   */
  public void logUnused() {
    for (String key : unused()) {
      log.warn("The configuration {} = {} was supplied but isn't a known config.", key,
               this.originals.get(key));
    }
  }

  /**
   * Get a configured instance of the give class specified by the given configuration key. If the
   * object implements Configurable configure it using the configuration.
   *
   * @param key The configuration key for the class
   * @param t   The interface the class should implement
   * @return A configured instance of the class
   */
  public <T> T getConfiguredInstance(String key, Class<T> t) {
    Class<?> c = getClass(key);
    if (c == null) {
      return null;
    }
    Object o = Utils.newInstance(c);
    if (!t.isInstance(o)) {
      throw new RuntimeException(c.getName() + " is not an instance of " + t.getName());
    }
    if (o instanceof Configurable) {
      ((Configurable) o).configure(this.originals);
    }
    return t.cast(o);
  }

  public <T> List<T> getConfiguredInstances(String key, Class<T> t) {
    List<String> klasses = getList(key);
    List<T> objects = new ArrayList<T>();
    for (String klass : klasses) {
      Class<?> c;
      try {
        c = Class.forName(klass);
      } catch (ClassNotFoundException e) {
        throw new ConfigException(key, klass, "Class " + klass + " could not be found.");
      }
      if (c == null) {
        return null;
      }
      Object o = Utils.newInstance(c);
      if (!t.isInstance(o)) {
        throw new RuntimeException(c.getName() + " is not an instance of " + t.getName());
      }
      if (o instanceof Configurable) {
        ((Configurable) o).configure(this.originals);
      }
      objects.add(t.cast(o));
    }
    return objects;
  }

  public static Properties getPropsFromFile(String propsFile) throws ConfigException {
    Properties props = new Properties();
    if (propsFile == null) {
      return props;
    }
    try {
      FileInputStream propStream = new FileInputStream(propsFile);
      props.load(propStream);
    } catch (IOException e) {
      throw new ConfigException("Couldn't load properties from " + propsFile, e);
    }
    return props;
  }

}
