/*
 * Copyright (c) 2020-2024 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.commons.protocol.serde;

import io.airbyte.commons.version.AirbyteProtocolVersion;
import io.airbyte.protocol.models.AirbyteMessage;
import jakarta.inject.Singleton;

/**
 * Serializer for Protocol V0.
 */
@Singleton
public class AirbyteMessageV0Serializer extends AirbyteMessageGenericSerializer<AirbyteMessage> {

  public AirbyteMessageV0Serializer() {
    super(AirbyteProtocolVersion.V0);
  }

}
