package com.example.data.repository

import com.example.data.database.MessageDao
import com.example.data.database.NotificationDao
import com.example.data.database.OrderDao
import com.example.data.firebase.FirebaseManager
import com.example.data.model.MessageEntity
import com.example.data.model.NotificationEntity
import com.example.data.model.OrderEntity
import kotlinx.coroutines.flow.Flow

class TradeRepository(
    private val orderDao: OrderDao,
    private val messageDao: MessageDao,
    private val notificationDao: NotificationDao
) {
    // Orders
    val allOrders: Flow<List<OrderEntity>> = orderDao.getAllOrders()

    fun getOrderById(id: Int): Flow<OrderEntity?> = orderDao.getOrderById(id)

    suspend fun createOrder(order: OrderEntity): Long {
        val id = orderDao.insertOrder(order)
        val finalOrder = if (order.id == 0) order.copy(id = id.toInt()) else order
        FirebaseManager.syncOrder(finalOrder)
        return id
    }

    suspend fun updateOrder(order: OrderEntity) {
        orderDao.updateOrder(order)
        FirebaseManager.syncOrder(order)
    }

    suspend fun deleteOrder(order: OrderEntity) {
        orderDao.deleteOrder(order)
    }

    // Messages
    val allMessages: Flow<List<MessageEntity>> = messageDao.getAllMessages()

    fun getMessagesForSupplier(supplierName: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForSupplier(supplierName)
    }

    suspend fun insertMessage(message: MessageEntity) {
        messageDao.insertMessage(message)
        FirebaseManager.syncMessage(message)
    }

    suspend fun clearMessages() {
        messageDao.clearMessages()
    }

    // Notifications
    val allNotifications: Flow<List<NotificationEntity>> = notificationDao.getAllNotifications()

    suspend fun insertNotification(notification: NotificationEntity) {
        notificationDao.insertNotification(notification)
        FirebaseManager.syncNotification(notification)
    }

    suspend fun markAllNotificationsAsRead() {
        notificationDao.markAllAsRead()
    }

    suspend fun deleteNotificationById(id: Int) {
        notificationDao.deleteNotificationById(id)
    }
}
