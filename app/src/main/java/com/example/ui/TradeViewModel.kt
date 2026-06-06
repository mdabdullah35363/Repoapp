package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.LogisticsLogEntry
import com.example.data.model.MessageEntity
import com.example.data.model.NotificationEntity
import com.example.data.model.OrderEntity
import com.example.data.model.Product
import com.example.data.model.ProductCatalog
import com.example.data.repository.TradeRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TradeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = TradeRepository(db.orderDao(), db.messageDao(), db.notificationDao())

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listMyType = Types.newParameterizedType(List::class.java, Map::class.java)
    private val jsonAdapter = moshi.adapter<List<Map<String, Any>>>(listMyType)

    // Search and filter states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    // Navigation and screen management
    private val _activeTab = MutableStateFlow("home") // home, messages, orders, notifications
    val activeTab = _activeTab.asStateFlow()

    private val _activeProduct = MutableStateFlow<Product?>(null)
    val activeProduct = _activeProduct.asStateFlow()

    private val _activeSupplierChat = MutableStateFlow<String?>(null)
    val activeSupplierChat = _activeSupplierChat.asStateFlow()

    // Product Catalog
    val products = ProductCatalog.sampleProducts

    // Filtered Products
    val filteredProducts: StateFlow<List<Product>> = combine(
        _searchQuery,
        _selectedCategory
    ) { query, category ->
        products.filter { product ->
            val matchesQuery = product.title.contains(query, ignoreCase = true) ||
                    product.supplierName.contains(query, ignoreCase = true) ||
                    product.category.contains(query, ignoreCase = true)
            val matchesCategory = category == "All" || product.category == category
            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), products)

    // Room Database Flows
    val orders: StateFlow<List<OrderEntity>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<MessageEntity>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntity>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Supplier Chat messages
    private val _supplierMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val supplierMessages = _supplierMessages.asStateFlow()

    // Checkout Flow States
    private val _checkoutProduct = MutableStateFlow<Product?>(null)
    val checkoutProduct = _checkoutProduct.asStateFlow()

    private val _checkoutQuantity = MutableStateFlow(1)
    val checkoutQuantity = _checkoutQuantity.asStateFlow()

    private val _isPaymentProcessing = MutableStateFlow(false)
    val isPaymentProcessing = _isPaymentProcessing.asStateFlow()

    private val _paymentSuccess = MutableStateFlow<Boolean?>(null)
    val paymentSuccess = _paymentSuccess.asStateFlow()

    // Active Order Detail for Logistics Tracking Map view
    private val _selectedOrderLogisticsId = MutableStateFlow<Int?>(null)
    val selectedOrderLogisticsId = _selectedOrderLogisticsId.asStateFlow()

    // Simulated overlay notification state (for real-world system popup inside app)
    private val _topNotificationBanner = MutableStateFlow<NotificationEntity?>(null)
    val topNotificationBanner = _topNotificationBanner.asStateFlow()

    init {
        // Seed default chat logs if empty
        viewModelScope.launch {
            val currentMsgs = repository.allMessages.first()
            if (currentMsgs.isEmpty()) {
                val seedData = listOf(
                    MessageEntity(
                        supplierName = "Shenzhen GreenEnergy Tech Ltd.",
                        supplierAvatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&auto=format&fit=crop&q=60",
                        messageText = "Hello! We saw your interest in the Apex Pro Solar Pumping systems. Let us know if you need customized pump widths or specific country certifications. We have extensive Trade Assurance coverages.",
                        timestamp = System.currentTimeMillis() - 86400000, // 24 hours ago
                        isFromSupplier = true,
                        productContextTitle = "Apex Pro Automated Solar Irrigation System",
                        productContextPrice = "$450.00 - $620.00 / Set"
                    ),
                    MessageEntity(
                        supplierName = "Ningbo Soundwave Industrial Co.",
                        supplierAvatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&auto=format&fit=crop&q=60",
                        messageText = "Greeting of the day! Regarding Acoustix ANC Earbuds X9, we can offer custom laser-printed logo branding on the case for orders over 200 units. Free packing gift boxes included.",
                        timestamp = System.currentTimeMillis() - 36000000, // 10 hours ago
                        isFromSupplier = true,
                        productContextTitle = "Acoustix Active Noise Cancelling Wireless Earbuds X9",
                        productContextPrice = "$8.50 - $12.90 / Piece"
                    )
                )
                seedData.forEach { repository.insertMessage(it) }
            }

            // Seed a welcome notification if notifications are empty
            val currentNotifications = repository.allNotifications.first()
            if (currentNotifications.isEmpty()) {
                repository.insertNotification(
                    NotificationEntity(
                        title = "Trade Assurance Active",
                        body = "Welcome to B2B Global Trade Portal. All orders are protected by secure Escrow holding. Pay securely and track logistics globally.",
                        timestamp = System.currentTimeMillis(),
                        isRead = false,
                        type = "TRADE_ASSURANCE"
                    )
                )
            }
        }
    }

    // Tab Navigation
    fun setActiveTab(tab: String) {
        _activeTab.value = tab
    }

    // Search and Category Filters
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    // Detail Screen Navigation
    fun viewProductDetails(product: Product) {
        _activeProduct.value = product
    }

    fun clearActiveProduct() {
        _activeProduct.value = null
    }

    // Supplier Messages Flow
    fun openSupplierChat(supplierName: String) {
        _activeSupplierChat.value = supplierName
        viewModelScope.launch {
            repository.getMessagesForSupplier(supplierName).collect { msgs ->
                // Sort or update local lists
                _supplierMessages.value = msgs
            }
        }
    }

    fun closeSupplierChat() {
        _activeSupplierChat.value = null
    }

    fun sendMessageToActiveSupplier(text: String) {
        val supplierName = _activeSupplierChat.value ?: return
        if (text.trim().isEmpty()) return

        val appNameContext = _activeProduct.value

        viewModelScope.launch {
            // Write buyer message
            val buyerMsg = MessageEntity(
                supplierName = supplierName,
                supplierAvatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&auto=format&fit=crop&q=60", // default placeholder avatar
                messageText = text,
                timestamp = System.currentTimeMillis(),
                isFromSupplier = false,
                productContextTitle = appNameContext?.title,
                productContextPrice = appNameContext?.let { "$${it.priceMin} - $${it.priceMax} / ${it.unit}" }
            )
            repository.insertMessage(buyerMsg)

            // Auto-refresh chat list locally
            openSupplierChat(supplierName)

            // Trigger Real-Time Supplier Response Simulation (3s delay)
            delay(2500)
            val supplierReplyText = getSupplierRealisticReply(supplierName, text, appNameContext)
            val supplierMsg = MessageEntity(
                supplierName = supplierName,
                supplierAvatarUrl = getSupplierAvatar(supplierName),
                messageText = supplierReplyText,
                timestamp = System.currentTimeMillis(),
                isFromSupplier = true,
                productContextTitle = appNameContext?.title,
                productContextPrice = appNameContext?.let { "$${it.priceMin} - $${it.priceMax} / ${it.unit}" }
            )
            repository.insertMessage(supplierMsg)

            // Trigger notification of incoming message
            val notif = NotificationEntity(
                title = "Message from $supplierName",
                body = if (supplierReplyText.length > 80) supplierReplyText.substring(0, 77) + "..." else supplierReplyText,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                type = "MESSAGE"
            )
            repository.insertNotification(notif)
            showInAppBanner(notif)

            // update chat representation
            openSupplierChat(supplierName)
        }
    }

    private fun getSupplierAvatar(supplier: String): String {
        return when {
            supplier.contains("GreenEnergy") -> "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=100&auto=format&fit=crop&q=60"
            supplier.contains("Soundwave") -> "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=100&auto=format&fit=crop&q=60"
            supplier.contains("Eco-Fiber") -> "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=100&auto=format&fit=crop&q=60"
            supplier.contains("Safety-Tech") -> "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=100&auto=format&fit=crop&q=60"
            supplier.contains("Aero Carbon") -> "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=100&auto=format&fit=crop&q=60"
            else -> "https://images.unsplash.com/photo-1519345182560-3f2917c472ef?w=100&auto=format&fit=crop&q=60"
        }
    }

    private fun getSupplierRealisticReply(supplier: String, query: String, product: Product?): String {
        val lowercaseQuery = query.lowercase()
        return when {
            lowercaseQuery.contains("price") || lowercaseQuery.contains("discount") || lowercaseQuery.contains("cheaper") -> {
                "We can offer a structured discount! For details: 5-10% off for double MOQ. We run safe escrow via Trade Assurance so your deposit is protected. Would you like me to draw up a proforma invoice?"
            }
            lowercaseQuery.contains("shipping") || lowercaseQuery.contains("delivery") || lowercaseQuery.contains("freight") -> {
                "We support FOB, CIF, and DDP shipments. Our logistics partners can clear customs at Chicago or Los Angeles regionals efficiently. Standard air shipping takes 4-7 business days, sea transport is 15-20 days."
            }
            lowercaseQuery.contains("custom") || lowercaseQuery.contains("oem") || lowercaseQuery.contains("logo") -> {
                "Absolutely! We support OEM custom designs. We can emboss logos on the body and print customized boxes. Minimum order is usually slightly higher, but let's confirm. Send us your vector files!"
            }
            lowercaseQuery.contains("sample") || lowercaseQuery.contains("test") -> {
                "We gladly support sample orders before placing secure bulk trade contracts! Simply hit our 'Order Samples' or check out on the product portal page."
            }
            else -> {
                "Thank you for contacting us from global coordinates. Yes, ${product?.title ?: "our catalog items"} are fully in stock and ready to pack if verified. Standard shipping is backed by Alibaba Trade Assurance protections. How many units do you forecast ordering?"
            }
        }
    }

    // Checkout / Place Order States
    fun openCheckout(product: Product) {
        _checkoutProduct.value = product
        _checkoutQuantity.value = product.moq
        _isPaymentProcessing.value = false
        _paymentSuccess.value = null
    }

    fun closeCheckout() {
        _checkoutProduct.value = null
    }

    fun setCheckoutQuantity(qty: Int) {
        val min = _checkoutProduct.value?.moq ?: 1
        _checkoutQuantity.value = if (qty < min) min else qty
    }

    fun processTradeAssurancePayment(bankName: String) {
        val product = _checkoutProduct.value ?: return
        val qty = _checkoutQuantity.value
        val total = (product.priceMin * qty) + (product.estShippingCostPerUnit * qty)

        _isPaymentProcessing.value = true
        _paymentSuccess.value = null

        viewModelScope.launch {
            // Simulate bank secure handshakes
            delay(2800)

            val trackingNo = "TRK-${(10000..99999).random()}-${product.supplierName.split(" ").firstOrNull()?.uppercase() ?: "CN"}"
            val initialLog = listOf(
                mapOf(
                    "status" to "ORDER_PLACED",
                    "description" to "Trade Assurance contract authorized. Buyer funds securely isolated in bank escrow.",
                    "location" to "Global Trade Escrow Hub",
                    "timestamp" to System.currentTimeMillis()
                )
            )
            val logJson = jsonAdapter.toJson(initialLog)

            val newOrder = OrderEntity(
                productTitle = product.title,
                productImageUrl = product.imageUrl,
                pricePerUnit = product.priceMin,
                quantity = qty,
                totalAmount = total,
                supplierName = product.supplierName,
                paymentStatus = "ESCROW_HELD",
                logisticsStatus = "ORDER_PLACED",
                trackingNumber = trackingNo,
                estimatedDelivery = "Est: ${SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(System.currentTimeMillis() + 14 * 86400000))}",
                logisticsHistoryJson = logJson
            )

            val newId = repository.createOrder(newOrder).toInt()

            // Construct order confirmation notifications
            val notifPayment = NotificationEntity(
                title = "Escrow Secured ($${String.format(Locale.US, "%.2f", total)})",
                body = "Funds for Contract $trackingNo secured under Escrow holding. Supplier notified to initiate shipping.",
                timestamp = System.currentTimeMillis() + 100,
                isRead = false,
                orderId = newId,
                type = "TRADE_ASSURANCE"
            )
            repository.insertNotification(notifPayment)

            _isPaymentProcessing.value = false
            _paymentSuccess.value = true

            // Trigger popups
            showInAppBanner(notifPayment)

            delay(1500)
            closeCheckout()
            // Switch tab to Logistics / Orders
            _activeTab.value = "orders"
            _selectedOrderLogisticsId.value = newId
        }
    }

    // Logistics Simulations
    fun viewLogistics(orderId: Int) {
        _selectedOrderLogisticsId.value = orderId
        _activeTab.value = "orders"
    }

    fun closeLogisticsDetails() {
        _selectedOrderLogisticsId.value = null
    }

    fun speedrunTransitTransition(orderId: Int) {
        viewModelScope.launch {
            // Fetch current order state
            val ords = orders.value
            val order = ords.find { it.id == orderId } ?: return@launch

            // Progress status step by step
            val nextStatusState = when (order.logisticsStatus) {
                "ORDER_PLACED" -> Triple(
                    "PROCESSING",
                    "Supplier packed, marked container, and generated commercial documents. Preparing customs sealing.",
                    "Supplier Warehouse (Shenzhen, CN)"
                )
                "PROCESSING" -> Triple(
                    "SHIPPED",
                    "Wholesale shipment verified by export customs and loaded onto container carrier 'Pacific Mariner'.",
                    "Shenzhen Port Sea Terminal, CN"
                )
                "SHIPPED" -> Triple(
                    "IN_TRANSIT",
                    "Ocean vessel anchored safely. Cargo cleared customs screening & EPA inspection, moved to warehouse depot.",
                    "Port of Los Angeles gateway, US"
                )
                "IN_TRANSIT" -> Triple(
                    "OUT_FOR_DELIVERY",
                    "Transferred to regional logistics provider. Cargo container dispatched via class-8 haul truck.",
                    "Local Distribution Terminal, US"
                )
                "OUT_FOR_DELIVERY" -> Triple(
                    "DELIVERED",
                    "Shipment arrived at destination unloading dock. Trade Assurance Delivery report signed and recorded.",
                    "Buyer Warehousing Hub, US"
                )
                "DELIVERED" -> Triple(
                    "ESCROW_RELEASED",
                    "Buyer verified wholesale shipment contents. Escrow funds successfully dispatched to Supplier.",
                    "Trade Assurance Escrow Ledger"
                )
                else -> return@launch // Already complete
            }

            val nextLogEntry = mapOf(
                "status" to nextStatusState.first,
                "description" to nextStatusState.second,
                "location" to nextStatusState.third,
                "timestamp" to System.currentTimeMillis()
            )

            // Parse existing logs and append
            val currentLogs = try {
                jsonAdapter.fromJson(order.logisticsHistoryJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            val updatedLogs = currentLogs + nextLogEntry
            val updatedLogsJson = jsonAdapter.toJson(updatedLogs)

            val updatedPaymentStatus = if (nextStatusState.first == "ESCROW_RELEASED") "COMPLETED" else order.paymentStatus

            val updatedOrder = order.copy(
                logisticsStatus = nextStatusState.first,
                logisticsHistoryJson = updatedLogsJson,
                paymentStatus = updatedPaymentStatus,
                lastLogisticsUpdate = System.currentTimeMillis()
            )

            repository.updateOrder(updatedOrder)

            // Trigger notification
            val notifTitle = when (nextStatusState.first) {
                "PROCESSING" -> "Order Processing: Packing"
                "SHIPPED" -> "Freight Shipped (Sea)"
                "IN_TRANSIT" -> "Cleared Customs"
                "OUT_FOR_DELIVERY" -> "Out for Local Delivery"
                "DELIVERED" -> "Cargo Delivered"
                "ESCROW_RELEASED" -> "Trade Escrow Released"
                else -> "Logistics Update"
            }

            val notifBody = "${order.productTitle}: ${nextStatusState.second}"
            val notif = NotificationEntity(
                title = notifTitle,
                body = notifBody,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                orderId = order.id,
                type = "LOGISTICS"
            )
            repository.insertNotification(notif)
            showInAppBanner(notif)
        }
    }

    // In-App Popup Banner Display system
    private fun showInAppBanner(notif: NotificationEntity) {
        viewModelScope.launch {
            _topNotificationBanner.value = notif
            // Play a simulated subtle delay sound or display banner for 4.5 seconds
            delay(4500)
            if (_topNotificationBanner.value?.id == notif.id || _topNotificationBanner.value?.timestamp == notif.timestamp) {
                _topNotificationBanner.value = null
            }
        }
    }

    fun dismissTopBanner() {
        _topNotificationBanner.value = null
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }

    fun deleteNotification(id: Int) {
        viewModelScope.launch {
            repository.deleteNotificationById(id)
        }
    }

    // Helper to extract raw logistics log entries for rendering in the Compose screen
    fun getLogisticsHistory(historyJson: String): List<LogisticsLogEntry> {
        return try {
            val list = jsonAdapter.fromJson(historyJson) ?: emptyList()
            list.map { map ->
                LogisticsLogEntry(
                    status = (map["status"] as? String) ?: "UNKNOWN",
                    description = (map["description"] as? String) ?: "",
                    location = (map["location"] as? String) ?: "",
                    timestamp = (map["timestamp"] as? Double)?.toLong() ?: System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
