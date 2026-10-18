package com.meshi.code.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.meshi.code.repository.ChatMessage
import com.meshi.code.repository.DeliveryState

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["senderDisplayId", "timestampMs", "channelIdx"], unique = true)],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderName: String,
    val senderDisplayId: String,
    val text: String,
    val timestampMs: Long,
    val isSentByMe: Boolean,
    val channelIdx: Int,
    val snrDb: Float,
    val ackTag: Long = 0L,
    val deliveryState: String = DeliveryState.NONE.name,
    val pathLen: Int = 0,
    val rttMs: Int = 0,
)

fun ChatMessageEntity.toDomain() = ChatMessage(
    senderName = senderName,
    senderDisplayId = senderDisplayId,
    text = text,
    timestampMs = timestampMs,
    isSentByMe = isSentByMe,
    channelIdx = channelIdx,
    snrDb = snrDb,
    ackTag = ackTag,
    deliveryState = DeliveryState.entries.firstOrNull { it.name == deliveryState }
        ?: DeliveryState.NONE,
    pathLen = pathLen,
    rttMs = rttMs,
)

fun ChatMessage.toEntity() = ChatMessageEntity(
    senderName = senderName,
    senderDisplayId = senderDisplayId,
    text = text,
    timestampMs = timestampMs,
    isSentByMe = isSentByMe,
    channelIdx = channelIdx,
    snrDb = snrDb,
    ackTag = ackTag,
    deliveryState = deliveryState.name,
    pathLen = pathLen,
    rttMs = rttMs,
)
