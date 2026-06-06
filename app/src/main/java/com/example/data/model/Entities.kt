package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class LogisticsLogEntry(
    val status: String,
    val description: String,
    val location: String,
    val timestamp: Long
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productTitle: String,
    val productImageUrl: String,
    val pricePerUnit: Double,
    val quantity: Int,
    val totalAmount: Double,
    val supplierName: String,
    val paymentStatus: String, // ESCROW_HELD, COMPLETED, REFUNDED
    val logisticsStatus: String, // PLACED, SHIPPED, IN_TRANSIT, DELIVERED, ESCROW_RELEASED
    val trackingNumber: String,
    val orderDate: Long = System.currentTimeMillis(),
    val estimatedDelivery: String,
    val lastLogisticsUpdate: Long = System.currentTimeMillis(),
    val logisticsHistoryJson: String // Stores serialized list of LogisticsLogEntry
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val supplierName: String,
    val supplierAvatarUrl: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromSupplier: Boolean,
    val productContextTitle: String? = null,
    val productContextPrice: String? = null
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val orderId: Int? = null,
    val type: String // LOGISTICS, TRADE_ASSURANCE, MESSAGE, SYSTEM
)
