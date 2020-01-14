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

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.camunda.bpm.application.ProcessApplicationReference;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.commons.logging.MdcAccess;

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
public class ProcessDataLoggingContext extends ProcessDataContext {

  private final StackValuePushHandler mdcStackValuePushHandler;

  private String propertyActivityId;
  private String propertyApplicationName;
  private String propertyBusinessKey;
  private String propertyDefinitionId;
  private String propertyInstanceId;
  private String propertyTenantId;

  private List<String> propertyNames = new ArrayList<>();

  public ProcessDataLoggingContext(ProcessEngineConfigurationImpl configuration) {
    propertyActivityId = configuration.getLoggingContextActivityId();
    if (isNotBlank(propertyActivityId)) {
      propertyNames.add(propertyActivityId);
    }
    propertyApplicationName = configuration.getLoggingContextApplicationName();
    if (isNotBlank(propertyApplicationName)) {
      propertyNames.add(propertyApplicationName);
    }
    propertyBusinessKey = configuration.getLoggingContextBusinessKey();
    if (isNotBlank(propertyBusinessKey)) {
      propertyNames.add(propertyBusinessKey);
    }
    propertyDefinitionId = configuration.getLoggingContextProcessDefinitionId();
    if (isNotBlank(propertyDefinitionId)) {
      propertyNames.add(propertyDefinitionId);
    }
    propertyInstanceId = configuration.getLoggingContextProcessInstanceId();
    if (isNotBlank(propertyInstanceId)) {
      propertyNames.add(propertyInstanceId);
    }
    propertyTenantId = configuration.getLoggingContextTenantId();
    if (isNotBlank(propertyTenantId)) {
      propertyNames.add(propertyTenantId);
    }
    mdcStackValuePushHandler = new MdcStackValuePushHandler(this);
  }

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
    if (!propertyNames.isEmpty()) {
      if (propertyValues.isEmpty()) {
        clearMdc();
      }
      startNewSection = true;
      addToStack(execution.getActivityId(), propertyActivityId);
      addToStack(execution.getProcessDefinitionId(), propertyDefinitionId);
      addToStack(execution.getProcessInstanceId(), propertyInstanceId);
      addToStack(execution.getTenantId(), propertyTenantId);

      if (isNotBlank(propertyApplicationName)) {
        ProcessApplicationReference currentPa = Context.getCurrentProcessApplication();
        if (currentPa != null) {
          addToStack(currentPa.getName(), propertyApplicationName);
        }
      }

      if (isNotBlank(propertyBusinessKey)) {
        addToStack(execution.getBusinessKey(), propertyBusinessKey);
      }

      if (!startNewSection) {
        // a new section was started
        return true;
      }
      startNewSection = false;
    }
    return false;
  }

  /**
   * Pop the latest section, remove all pushed properties of that section and
   * update the MDC accordingly
   */
  public void popSection() {
    if (!propertyNames.isEmpty()) {
      super.popSection();
    }
  }

  /** Remove all logging context properties from the MDC */
  public void clearMdc() {
    for (String property : propertyNames) {
      MdcAccess.remove(property);
    }
  }

  /** Update the MDC with the current values of this logging context */
  public void update() {
    if (!propertyValues.isEmpty()) {
      // only update MDC if this context has set anything as well
      for (String property : propertyNames) {
        updateMdc(property);
      }
    }
  }

  /** Preserve the current MDC values and store them in the logging context */
  public void fetchCurrentContext() {
    if (!propertyNames.isEmpty()) {
      startNewSection = true;
      for (String property : propertyNames) {
        addToStack(MdcAccess.get(property), property, stackValuePushHandler);
      }
      startNewSection = false;
    }
  }

  protected void removeFromStack(String property) {
    super.removeFromStack(property);
    updateMdc(property);
  }

  protected void updateMdc(String property) {
    String previousValue = propertyValues.containsKey(property) ? propertyValues.get(property).peekFirst() : null;
    if (isNull(previousValue)) {
      MdcAccess.remove(property);
    } else {
      MdcAccess.put(property, previousValue);
    }
  }

  private void addToStack(String value, String property) {
    addToStack(value, property, mdcStackValuePushHandler);
  }

  public static class MdcStackValuePushHandler extends StackValuePushHandler {

    public MdcStackValuePushHandler(ProcessDataContext context) {
      super(context);
    }

    @Override
    public void nullValueAdded(Deque<String> deque, String property) {
      super.nullValueAdded(deque, property);
      MdcAccess.remove(property);
    }

    @Override
    public void valueAdded(Deque<String> deque, String property, String value) {
      super.valueAdded(deque, property, value);
      MdcAccess.put(property, value);
    }
  }
}
