/*
 * Copyright (c) 2020-2024 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.commons.temporal;

/**
 * Error code for temporal jobs run at Airbyte.
 */
public enum ErrorCode {
  UNKNOWN,
  WORKFLOW_DELETED,
  WORKFLOW_RUNNING
}
