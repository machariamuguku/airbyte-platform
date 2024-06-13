package io.airbyte.workers.internal.bookkeeping.streamstatus

import com.google.common.annotations.VisibleForTesting
import io.airbyte.api.client.model.generated.StreamStatusRateLimitedMetadata
import io.airbyte.api.client.model.generated.StreamStatusRead
import io.airbyte.featureflag.Connection
import io.airbyte.featureflag.Multi
import io.airbyte.featureflag.ProcessRateLimitedMessage
import io.airbyte.featureflag.Workspace
import io.airbyte.protocol.models.AirbyteMessage
import io.airbyte.protocol.models.AirbyteStateMessage
import io.airbyte.protocol.models.AirbyteStateMessage.AirbyteStateType
import io.airbyte.protocol.models.AirbyteTraceMessage
import io.airbyte.workers.context.ReplicationContext
import io.airbyte.workers.general.CachingFeatureFlagClient
import io.airbyte.workers.general.RateLimitedMessageHelper
import io.airbyte.workers.helper.AirbyteMessageDataExtractor
import io.airbyte.workers.internal.bookkeeping.events.StreamStatusUpdateEvent
import io.airbyte.workers.models.StateWithId
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micronaut.context.event.ApplicationEventPublisher
import io.airbyte.api.client.model.generated.StreamStatusRunState as ApiEnum
import io.airbyte.protocol.models.AirbyteStreamStatusTraceMessage.AirbyteStreamStatus as ProtocolEnum

private val logger = KotlinLogging.logger {}

/**
 * Responds to messages from the source and destination and maps them to the appropriate state updates
 * in the store.
 *
 * When state changes occur, it dispatches StreamStatusUpdateEvents for reconciling with the API.
 *
 * Dispatch layer.
 */
class StreamStatusTracker(
  private val dataExtractor: AirbyteMessageDataExtractor,
  private val store: StreamStatusStateStore,
  private val eventPublisher: ApplicationEventPublisher<StreamStatusUpdateEvent>,
  private val ffClient: CachingFeatureFlagClient,
  private val ctx: ReplicationContext,
  // TODO: move cache to client proper when Docker uses Orchestrator
  // Cache for api responses — we put this here so it gets GC'd when the sync
  // finishes for Docker. The client is a singleton and in Docker runs in the worker
  // so will never be torn down, so we create it in the Tracker which is unique per sync.
  private val apiResponseCache: MutableMap<StreamStatusKey, StreamStatusRead> = HashMap(),
) {
  fun track(msg: AirbyteMessage) {
    val key = dataExtractor.getStreamFromMessage(msg)?.let { StreamStatusKey.fromProtocol(it) }
    if (key == null) {
      logger.debug { "Unable to read stream descriptor from message of type: ${msg.type}. Skipping..." }
      return
    }

    logger.debug { "Message for stream ${key.toDisplayName()} received of type: ${msg.type}" }

    val currentRunState = store.get(key)?.runState

    val updatedStatus =
      when (msg.type) {
        AirbyteMessage.Type.TRACE -> {
          if (msg.trace.type == AirbyteTraceMessage.Type.STREAM_STATUS) {
            trackEvent(key, msg.trace)
          } else {
            logger.debug {
              "Stream Status does not track TRACE messages of type: ${msg.trace.type}. Ignoring message for stream ${key.toDisplayName()}"
            }
            null
          }
        }
        AirbyteMessage.Type.RECORD -> trackRecord(key)
        AirbyteMessage.Type.STATE -> {
          if (msg.state.type == AirbyteStateType.STREAM) {
            trackState(key, msg.state)
          } else {
            logger.debug {
              "Stream Status does not track STATE messages of type: ${msg.state.type}. Ignoring message for stream ${key.toDisplayName()}"
            }
            null
          }
        }
        else -> {
          logger.debug { "Stream Status does not track message of type: ${msg.type}. Ignoring message for stream ${key.toDisplayName()}" }
          null
        }
      }

    if (updatedStatus != null && updatedStatus.runState != currentRunState) {
      logger.info { "Sending update for ${key.toDisplayName()} - $currentRunState -> ${updatedStatus.runState}" }
      sendUpdate(key, updatedStatus.runState!!, updatedStatus.metadata)
    }
  }

  @VisibleForTesting
  fun trackEvent(
    key: StreamStatusKey,
    msg: AirbyteTraceMessage,
  ): StreamStatusValue {
    logger.info { "Stream status TRACE received of status: ${msg.streamStatus.status} for stream ${key.toDisplayName()}" }

    return when (msg.streamStatus.status!!) {
      ProtocolEnum.STARTED -> store.setRunState(key, ApiEnum.RUNNING)
      ProtocolEnum.RUNNING -> {
        if (RateLimitedMessageHelper.isStreamStatusRateLimitedMessage(msg.streamStatus) && shouldProcessRateLimitedMessage()) {
          logger.info { "Stream status TRACE with status RUNNING received of sub-type: ${ApiEnum.RATE_LIMITED} for stream ${key.toDisplayName()}" }

          store.setRunState(key, ApiEnum.RATE_LIMITED)
          store.setMetadata(key, RateLimitedMessageHelper.apiFromProtocol(msg.streamStatus))
        } else {
          store.setRunState(key, ApiEnum.RUNNING)
        }
      }
      ProtocolEnum.INCOMPLETE -> store.setRunState(key, ApiEnum.INCOMPLETE)
      ProtocolEnum.COMPLETE -> store.markSourceComplete(key)
    }
  }

  @VisibleForTesting
  fun trackRecord(key: StreamStatusKey): StreamStatusValue {
    if (store.isRateLimited(key)) {
      store.setMetadata(key, null)
    }

    store.setRunState(key, ApiEnum.RUNNING)
    return store.markStreamNotEmpty(key)
  }

  @VisibleForTesting
  fun trackState(
    key: StreamStatusKey,
    msg: AirbyteStateMessage,
  ): StreamStatusValue {
    val id = StateWithId.getIdFromStateMessage(msg)
    logger.debug { "STATE with id $id for ${key.toDisplayName()}" }

    return if (!store.isDestComplete(key, id)) {
      store.setLatestStateId(key, id)
    } else {
      logger.info { "Destination complete for ${key.toDisplayName()}" }
      store.setRunState(key, ApiEnum.COMPLETE)
    }
  }

  private fun sendUpdate(
    key: StreamStatusKey,
    runState: ApiEnum,
    metadata: StreamStatusRateLimitedMetadata?,
  ) {
    eventPublisher.publishEvent(StreamStatusUpdateEvent(apiResponseCache, key, runState, metadata, ctx))
  }

  private fun shouldProcessRateLimitedMessage(): Boolean {
    val ffCtx = Multi(listOf(Workspace(ctx.workspaceId), Connection(ctx.connectionId)))
    return ffClient.boolVariation(ProcessRateLimitedMessage, ffCtx)
  }
}
