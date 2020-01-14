/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.engine.impl.interceptor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;

/**
 * Holds the contextual process data.<br>
 *
 * New context properties are always part of a section that can be started by
 * {@link #pushSection(ExecutionEntity)}. The section keeps track of all pushed
 * properties. Those can easily be cleared by popping the section with
 * {@link #popSection()} afterwards, e.g. after the successful execution.<br>
 *
 * </ul>
 */
public class ProcessDataContext {

  public static final String PROPERTY_ACTIVITY_ID = "activityId";

  protected static final String NULL_VALUE = "~NULL_VALUE~";

  protected Map<String, Deque<String>> propertyValues = new HashMap<>();

  protected boolean startNewSection = false;
  protected Deque<List<String>> sections = new ArrayDeque<>();

  protected final StackValuePushHandler stackValuePushHandler;

  public ProcessDataContext() {
    this.stackValuePushHandler = new StackValuePushHandler(this);
  }

  /**
   * Start a new section that keeps track of the pushed properties
   *
   * @param execution
   *          the execution to retrieve the logging context data from
   *
   * @return <code>true</code> if the section contains any updates and therefore
   *         should be popped later by {@link #popSection()}
   */
  public boolean pushSection(ExecutionEntity execution) {
    startNewSection = true;
    addToStack(execution.getActivityId(), PROPERTY_ACTIVITY_ID, stackValuePushHandler);

    // a new section was started
    return !startNewSection;
  }

  /**
   * Pop the latest section and remove all pushed properties of that section
   */
  public void popSection() {
    List<String> section = sections.pollFirst();
    if (section != null) {
      for (String property : section) {
        removeFromStack(property);
      }
    }
  }

  /**
   * @param property
   *          the property to retrieve the latest value for
   * @return the latest value of the property if there is one, <code>null</code>
   *         otherwise
   */
  public String getLatestPropertyValue(String property) {
    if (!propertyValues.isEmpty()) {
      Deque<String> deque = propertyValues.get(property);
      if (deque != null) {
        return deque.peekFirst();
      }
    }
    return null;
  }

  protected void addToStack(String value, String property, StackValuePushHandler stackValuePushHandler) {
    if (!isNotBlank(property)) {
      return;
    }
    Deque<String> deque = getDeque(property);
    String current = deque.peekFirst();
    if (valuesEqual(current, value)) {
      return;
    }
    addToCurrentSection(property);
    if (value == null) {
      stackValuePushHandler.nullValueAdded(deque, property);
    } else {
      stackValuePushHandler.valueAdded(deque, property, value);
    }
  }

  protected void removeFromStack(String property) {
    if (property == null) {
      return;
    }
    getDeque(property).removeFirst();
  }

  protected Deque<String> getDeque(String property) {
    Deque<String> deque = propertyValues.get(property);
    if (deque == null) {
      deque = new ArrayDeque<>();
      propertyValues.put(property, deque);
    }
    return deque;
  }

  protected void addToCurrentSection(String property) {
    List<String> section = sections.peekFirst();
    if (startNewSection) {
      section = new ArrayList<>();
      sections.addFirst(section);
      startNewSection = false;
    }
    section.add(property);
  }

  protected static boolean isNotBlank(String property) {
    return property != null && !property.trim().isEmpty();
  }

  protected static boolean valuesEqual(String val1, String val2) {
    if (isNull(val1)) {
      return val2 == null;
    }
    return val1.equals(val2);
  }

  protected static boolean isNull(String value) {
    return value == null || NULL_VALUE.equals(value);
  }

  public static class StackValuePushHandler {

    protected ProcessDataContext context;

    public StackValuePushHandler(ProcessDataContext context) {
      this.context = context;
    }

    public void nullValueAdded(Deque<String> deque, String property) {
      deque.addFirst(NULL_VALUE);
    }

    public void valueAdded(Deque<String> deque, String property, String value) {
      deque.addFirst(value);
    }
  }
}
