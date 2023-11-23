/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.temporal.sync;

import io.airbyte.config.StandardSyncOutput;
import io.airbyte.workers.models.ReplicationActivityInput;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * ReplicationActivity.
 */
@ActivityInterface
public interface ReplicationActivity {

  @ActivityMethod
  StandardSyncOutput replicateV2(final ReplicationActivityInput replicationInput);

}
