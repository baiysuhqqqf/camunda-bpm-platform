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

import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;

/**
 * Holds the contextual process data used in logging.<br>
 *
 * New context properties are always part of a section that can be started by
 * {@link #pushSection(ExecutionEntity)}. The section keeps track of all pushed
 * properties. Those can easily be cleared by popping the section with
 * {@link #popSection()} afterwards, e.g. after the successful execution.<br>
 *
 * A property is only pushed to the logging context if there is a configured
 * non-empty context name for it in the {@link ProcessEngineConfigurationImpl
 * process engine configuration}. The following configuration options are
 * available:
 * <ul>
 * <li>loggingContextActivityId - the context property for the activity id</li>
 * <li>loggingContextApplicationName - the context property for the application name</li>
 * <li>loggingContextBusinessKey - the context property for the business key</li>
 * <li>loggingContextDefinitionId - the context property for the definition id</li>
 * <li>loggingContextProcessInstanceId - the context property for the instance id</li>
 * <li>loggingContextTenantId - the context property for the tenant id</li>
 * </ul>
 */
public class ProcessDataContext {

  public static final String PROPERTY_ACTIVITY_ID = "activityId";

  private static final String NULL_VALUE = "~NULL_VALUE~";

  private Map<String, Deque<String>> propertyValues = new HashMap<>();

  private boolean startNewSection = false;
  private Deque<List<String>> sections = new ArrayDeque<>();

  /**
   * Start a new section that keeps track of the pushed properties and update
   * the MDC. This also includes clearing the MDC for the first section that is
   * pushed for the logging context so that only the current properties will be
   * present in the MDC (might be less than previously present in the MDC). The
   * previous logging context needs to be reset in the MDC when this one is
   * closed. This can be achieved by using {@link #updateMdc(String)} with the
   * previous logging context.
   *
   * @param execution
   *          the execution to retrieve the logging context data from
   *
   * @return <code>true</code> if the section contains any updates for the MDC
   *         and therefore should be popped later by {@link #popSection()}
   */
  public boolean pushSection(ExecutionEntity execution) {
    startNewSection = true;
    addToStack(execution.getActivityId(), PROPERTY_ACTIVITY_ID);

    // a new section was started
    return !startNewSection;
  }

  /**
   * Pop the latest section, remove all pushed properties of that section and
   * update the MDC accordingly
   */
  public void popSection() {
    List<String> section = sections.pollFirst();
    if (section != null) {
      for (String property : section) {
        removeFromStack(property);
      }
    }
  }

  public String getLatestProperty(String property) {
    if (!propertyValues.isEmpty()) {
      return propertyValues.get(property).peekFirst();
    }
    return null;
  }

  protected void addToStack(String value, String property) {
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
      deque.addFirst(NULL_VALUE);
    } else {
      deque.addFirst(value);
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

}
