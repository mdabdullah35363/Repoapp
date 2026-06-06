@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.data.firebase.FirebaseManager
import com.example.data.model.MessageEntity
import com.example.data.model.NotificationEntity
import com.example.data.model.OrderEntity
import com.example.data.model.Product
import com.example.ui.TradeViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeHubApp(viewModel: TradeViewModel) {
    val activeTab by viewModel.activeTab.collectAsState()
    val activeProduct by viewModel.activeProduct.collectAsState()
    val activeSupplierChat by viewModel.activeSupplierChat.collectAsState()
    val checkoutProduct by viewModel.checkoutProduct.collectAsState()
    val selectedOrderLogisticsId by viewModel.selectedOrderLogisticsId.collectAsState()
    val topNotificationBanner by viewModel.topNotificationBanner.collectAsState()

    val orders by viewModel.orders.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val messages by viewModel.messages.collectAsState()

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main Screen Scaffold (respecting notch padding with safeDrawing)
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (activeProduct == null && activeSupplierChat == null) {
                    TradeBottomNavigation(
                        activeTab = activeTab,
                        onTabSelected = { viewModel.setActiveTab(it) },
                        ordersCount = orders.count { it.logisticsStatus != "DELIVERED" && it.logisticsStatus != "ESCROW_RELEASED" },
                        notificationsCount = notifications.count { !it.isRead },
                        messagesCount = messages.distinctBy { it.supplierName }.size // simplified grouping count
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Screen Selection Routing
                when (activeTab) {
                    "home" -> ProductCatalogScreen(viewModel = viewModel)
                    "messages" -> SupplierMessagesScreen(viewModel = viewModel)
                    "orders" -> LogisticsOrdersScreen(viewModel = viewModel)
                    "notifications" -> NotificationsAlertsScreen(viewModel = viewModel)
                }

                // If Product Details overlay is active
                activeProduct?.let { product ->
                    ProductDetailsScreen(
                        product = product,
                        onClose = { viewModel.clearActiveProduct() },
                        onChatSupplier = {
                            viewModel.clearActiveProduct()
                            viewModel.setActiveTab("messages")
                            viewModel.openSupplierChat(product.supplierName)
                        },
                        onOpenCheckout = {
                            viewModel.openCheckout(product)
                        }
                    )
                }

                // Supplier Active Chat overlay
                activeSupplierChat?.let { supplierName ->
                    ActiveChatScreen(
                        supplierName = supplierName,
                        viewModel = viewModel,
                        onClose = { viewModel.closeSupplierChat() }
                    )
                }
            }
        }

        // Animated App-Internal Realtime Push Notification Overlay Banner
        AnimatedVisibility(
            visible = topNotificationBanner != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(16.dp)
                .zIndex(99f)
        ) {
            topNotificationBanner?.let { notif ->
                InAppNotificationBanner(
                    notification = notif,
                    onDismiss = { viewModel.dismissTopBanner() },
                    onClick = {
                        viewModel.dismissTopBanner()
                        if (notif.orderId != null) {
                            viewModel.viewLogistics(notif.orderId)
                        } else if (notif.type == "MESSAGE") {
                            viewModel.setActiveTab("messages")
                        } else {
                            viewModel.setActiveTab("notifications")
                        }
                    }
                )
            }
        }

        // Secure Escrow Checkout Sheet
        if (checkoutProduct != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeCheckout() },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                checkoutProduct?.let { product ->
                    CheckoutPaymentSheet(
                        product = product,
                        viewModel = viewModel,
                        onClose = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                viewModel.closeCheckout()
                            }
                        }
                    )
                }
            }
        }
    }
}

// Bottom Navigation styling according to design guidelines (M3 pills)
@Composable
fun TradeBottomNavigation(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    ordersCount: Int,
    notificationsCount: Int,
    messagesCount: Int
) {
    NavigationBar(
        tonalElevation = 8.dp,
        modifier = Modifier.shadow(16.dp)
    ) {
        NavigationBarItem(
            selected = activeTab == "home",
            onClick = { onTabSelected("home") },
            label = { Text("Storefront", fontWeight = FontWeight.Medium) },
            icon = {
                Icon(
                    imageVector = if (activeTab == "home") Icons.Filled.Storefront else Icons.Outlined.Storefront,
                    contentDescription = "Storefront Catalog"
                )
            },
            modifier = Modifier.testTag("nav_storefront")
        )

        NavigationBarItem(
            selected = activeTab == "messages",
            onClick = { onTabSelected("messages") },
            label = { Text("Inquiries", fontWeight = FontWeight.Medium) },
            icon = {
                BadgedBox(
                    badge = {
                        if (messagesCount > 0) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text(messagesCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (activeTab == "messages") Icons.Filled.QuestionAnswer else Icons.Outlined.QuestionAnswer,
                        contentDescription = "Supplier Chat"
                    )
                }
            },
            modifier = Modifier.testTag("nav_inquiries")
        )

        NavigationBarItem(
            selected = activeTab == "orders",
            onClick = { onTabSelected("orders") },
            label = { Text("Shipments", fontWeight = FontWeight.Medium) },
            icon = {
                BadgedBox(
                    badge = {
                        if (ordersCount > 0) {
                            Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                Text(ordersCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (activeTab == "orders") Icons.Filled.LocalShipping else Icons.Outlined.LocalShipping,
                        contentDescription = "Logistics Tracking"
                    )
                }
            },
            modifier = Modifier.testTag("nav_shipments")
        )

        NavigationBarItem(
            selected = activeTab == "notifications",
            onClick = { onTabSelected("notifications") },
            label = { Text("Alerts", fontWeight = FontWeight.Medium) },
            icon = {
                BadgedBox(
                    badge = {
                        if (notificationsCount > 0) {
                            Badge(containerColor = Color.Red) {
                                Text(notificationsCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (activeTab == "notifications") Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        contentDescription = "Trade Notifications Alerts"
                    )
                }
            },
            modifier = Modifier.testTag("nav_alerts")
        )
    }
}

// STOREFRONT TAB: Product Catalog screen with filters
@Composable
fun FirebaseStatusBanner() {
    var showSetupDialog by remember { mutableStateOf(false) }
    val isInitialized = FirebaseManager.isFirebaseInitialized

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { showSetupDialog = true },
        colors = CardDefaults.cardColors(
            containerColor = if (isInitialized) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isInitialized) Color(0xFF10B981) else Color(0xFFF59E0B),
                        shape = CircleShape
                    )
            )
            Text(
                text = if (isInitialized) 
                    "Google Firebase Cloud Active" 
                else 
                    "Firebase Offline (Local Room-DB Live)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isInitialized) 
                    MaterialTheme.colorScheme.onPrimaryContainer
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1.0f)
            )
            Icon(
                imageVector = Icons.Filled.Cloud,
                contentDescription = "Sync Status",
                tint = if (isInitialized) 
                    MaterialTheme.colorScheme.primary
                else 
                    MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = { showSetupDialog = false },
            confirmButton = {
                TextButton(onClick = { showSetupDialog = false }) {
                    Text("Got it")
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info, 
                        contentDescription = "Firebase Setup",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Google Firebase Integration", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isInitialized) 
                            "The app is successfully connected and synchronized in real-time with Google Firebase Cloud Firestore & Realtime Database! All B2B catalog items, messaging threads, and logistics reports are pushed to your secure cloud console."
                        else "This app features a production-grade Google Firebase architecture! Currently, it is running in high-performance local offline Mode (Room SQLite) because your personal credentials are not configured yet.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (!isInitialized) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "How to activate Google Firebase sync:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val steps = listOf(
                            "1. Create an Android project in the Google Firebase Console.",
                            "2. Navigate to project Settings and locate API key & App ID strings.",
                            "3. Add these four parameters to your AI Studio Secrets panel:",
                            "   • FIREBASE_API_KEY\n   • FIREBASE_PROJECT_ID\n   • FIREBASE_DATABASE_URL\n   • FIREBASE_APP_ID",
                            "4. Re-compile or reload. The status indicator will turn green!"
                        )
                        steps.forEach { step ->
                            Text(
                                text = step,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun ProductCatalogScreen(viewModel: TradeViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val filteredProducts by viewModel.filteredProducts.collectAsState()

    val categories = listOf("All", "Machinery & Equipment", "Consumer Electronics", "Home & Packaging", "Industrial & Safety", "Sports & Fitness")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Large Premium Headline
        Text(
            text = "Global B2B Sourcing",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Manufacturer deals with secure Trade Assurance escrow",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        FirebaseStatusBanner()

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar with custom clear buttons
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search machinery, electronics, apparel...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search icon") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_field"),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Categories sliding row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setSelectedCategory(category) },
                    label = { Text(category, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                        borderWidth = 1.dp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // B2B Wholesale Staggered Listings
        if (filteredProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Inventory,
                        contentDescription = "Search empty",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No cargo matches your specifications",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Try searching broader terms or switching factory classifications.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                products = filteredProducts,
                onProductClick = { viewModel.viewProductDetails(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("product_listings_grid")
            )
        }
    }
}

// B2B Grid items
@Composable
fun LazyVerticalGrid(
    products: List<Product>,
    onProductClick: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier
    ) {
        items(products) { product ->
            WholesaleCard(product = product, onClick = { onProductClick(product) })
        }
    }
}

// Premium Wholesale-focused card listing
@Composable
fun WholesaleCard(product: Product, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Image with visual depth and fallback status
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .size(110.dp)
                    .align(Alignment.CenterVertically)
            ) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                // Category badge
                Text(
                    text = product.category,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Title
                Text(
                    text = product.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Bulk pricing list
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", product.priceMin)} - $${String.format(Locale.US, "%.2f", product.priceMax)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = " / ${product.unit}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Minimum Order Quantity (MOQ) pill & rating
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // MOQ
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFF3F4F6), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MOQ: ${product.moq} ${product.unit}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4B5563)
                        )
                    }

                    // Rating
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Rating star",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = product.supplierRating.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Vendor label
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Verified Factory logo",
                        tint = Color(0xFFFF8A00),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${product.supplierName} (${product.supplierYears} Yrs)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4B5563),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// OVERLAY: Product Details View
@Composable
fun ProductDetailsScreen(
    product: Product,
    onClose: () -> Unit,
    onChatSupplier: () -> Unit,
    onOpenCheckout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = false) {} // block click throughs
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Image Header with close overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Close floating circle
                IconButton(
                    onClick = { onClose() },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.4f), shape = CircleShape)
                        .testTag("details_back")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            // Core Specs Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Verified supplier banner
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEF3C7), shape = RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "Trade assurance logo",
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Trade Assurance Protected",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = "Escrow safeguards refunds and dispatch schedules.",
                            fontSize = 11.sp,
                            color = Color(0xFFB45309)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = product.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Wholesale Price ranges
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", product.priceMin)} - $${String.format(Locale.US, "%.2f", product.priceMax)}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " / Unit (${product.unit})",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Basic Specifications Table
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Transaction Specifications",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        DetailRow("Minimum Order (MOQ)", "${product.moq} ${product.unit}")
                        DetailRow("Factory Location", product.supplierCountry)
                        DetailRow("Quality Standard", product.certificate)
                        DetailRow("Warranty Plan", product.warranty)
                        DetailRow("Estimated Freight", "$${product.estShippingCostPerUnit} par unit (${product.unit})")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Detailed Technical Specifications
                Text(
                    text = "Description & Capacity",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = product.description,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Supplier overview card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Business, contentDescription = "Supplier logo", tint = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = product.supplierName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1F2937)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.VerifiedUser, "Shield verified", tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Verified Gold Supplier | ${product.supplierYears} years",
                                    fontSize = 11.sp,
                                    color = Color(0xFF4B5563)
                                )
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = CircleShape
                        ) {
                            Text(
                                text = " ★ ${product.supplierRating} ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD97706),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp)) // padding for bottom bar
            }
        }

        // Floating Bottom Actions Bar (Contact Supplier & Secure Checkout Buy NOW)
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(16.dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Secondary Inquire chat Button
                OutlinedButton(
                    onClick = { onChatSupplier() },
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                        .testTag("btn_contact_supplier"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Sms, contentDescription = "Chat icon")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chat Supplier", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                // Primary secure Lock escrow buy Button
                Button(
                    onClick = { onOpenCheckout() },
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1.2f)
                        .testTag("btn_secure_buy"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "Secure Payment Escrow Lock")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buy Now (Escrow)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

// BOTTOM SHEET overlay: Secure checkout ledger payment logic
@Composable
fun CheckoutPaymentSheet(
    product: Product,
    viewModel: TradeViewModel,
    onClose: () -> Unit
) {
    val qty by viewModel.checkoutQuantity.collectAsState()
    val isProcessing by viewModel.isPaymentProcessing.collectAsState()
    val success by viewModel.paymentSuccess.collectAsState()

    val banks = listOf(
        "CitiBank Trade Assurance Escrow AC-931",
        "HSBC Global Escrow Account US-809",
        "Chase Commercial Brokerage Escrow"
    )
    var selectedBank by remember { mutableStateOf(banks.first()) }
    var menuExpanded by remember { mutableStateOf(false) }

    val unitPrice = product.priceMin
    val totalProd = unitPrice * qty
    val shippingCharge = product.estShippingCostPerUnit * qty
    val totalOverall = totalProd + shippingCharge

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trade Assurance Escrow Contract",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = { onClose() }) {
                Icon(Icons.Filled.Close, contentDescription = "Close Checkout Sheet")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (success == true) {
            // SUCCESS CHECKOUT GRAPH
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(TrustGreen.copy(alpha = 0.15f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Success",
                        tint = TrustGreen,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Contract Secured Successfully!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TrustGreen
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Funds lock authorized. Tracking shipment codes.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        } else if (isProcessing) {
            // SECURE TRANSFERS BANK HANDSHAKES
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Establishing Secure Bank Handshakes...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Verifying Trade Assurance dynamic trust ledger...",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        } else {
            // STANDARD FORM CHECKOUT
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    // Cargo review
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(60.dp)
                        ) {
                            AsyncImage(
                                model = product.imageUrl,
                                contentDescription = product.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = product.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Supplier: ${product.supplierName}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", unitPrice)} / ${product.unit}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                item {
                    // Wholesale quantity controller ensuring MOQ
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Order Quantity", fontSize = 12.sp, color = Color.Gray)
                                Text("Min MOQ: ${product.moq}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Card(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable { viewModel.setCheckoutQuantity(qty - 5) }
                                        .testTag("qty_decrease_btn"),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = CircleShape
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("-", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                    }
                                }

                                Text(
                                    text = qty.toString(),
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp)
                                        .testTag("qty_value_text"),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                Card(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable { viewModel.setCheckoutQuantity(qty + 5) }
                                        .testTag("qty_increase_btn"),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = CircleShape
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("+", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    // Safe escrow Escrow account selection
                    Text("Select Secured Settlement Escrow Escrow Bank", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = menuExpanded,
                            onExpandedChange = { menuExpanded = !menuExpanded }
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = selectedBank,
                                onValueChange = {},
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                banks.forEach { bank ->
                                    DropdownMenuItem(
                                        text = { Text(bank, fontSize = 13.sp) },
                                        onClick = {
                                            selectedBank = bank
                                            menuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    // Fees summary
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Industrial Cargo Subtotal", fontSize = 12.sp, color = Color.Gray)
                                Text("$${String.format(Locale.US, "%.2f", totalProd)}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Wholesale Import Freight Est.", fontSize = 12.sp, color = Color.Gray)
                                Text("$${String.format(Locale.US, "%.2f", shippingCharge)}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Secured Contract Total", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                Text("$${String.format(Locale.US, "%.2f", totalOverall)}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                item {
                    // Escrow safety checkbox check
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFECFDF5), shape = RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.VerifiedUser, "Shield check logo", tint = TrustGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Escrow Policy: Funds are locked strictly and discharged when you sign shipping cargo confirmation reports.",
                            fontSize = 10.sp,
                            color = Color(0xFF047857),
                            lineHeight = 13.sp
                        )
                    }
                }

                item {
                    // Escrow authorize action
                    Button(
                        onClick = { viewModel.processTradeAssurancePayment(selectedBank) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_authorize_escrow"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.LockOpen, contentDescription = "Authorize escrow lock logo")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Secure Escrow Payment ($${String.format(Locale.US, "%.2f", totalOverall)})", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// INQUIRIES CHATS TAB screen
@Composable
fun SupplierMessagesScreen(viewModel: TradeViewModel) {
    val messages by viewModel.messages.collectAsState()
    val groupConversations = messages.groupBy { it.supplierName }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Manufacturer Inquiries",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Direct negotiations with verified manufacturers & exporters",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (groupConversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Forum,
                        contentDescription = "Empty Inquiries logo",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Sourcing Inquiries Placed",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Click 'Chat Supplier' on product details to initiate secure trade talks.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("inquiries_conversations_list")
            ) {
                items(groupConversations.keys.toList()) { supplier ->
                    val supplierChatList = groupConversations[supplier] ?: emptyList()
                    val latestMsg = supplierChatList.lastOrNull()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openSupplierChat(supplier) }
                            .testTag("conversation_supplier_$supplier"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Card(
                                shape = CircleShape,
                                modifier = Modifier.size(46.dp)
                            ) {
                                AsyncImage(
                                    model = latestMsg?.supplierAvatarUrl,
                                    contentDescription = "Avatar supplier",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = supplier,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val timeString = latestMsg?.let {
                                        SimpleDateFormat("HH:mm", Locale.US).format(Date(it.timestamp))
                                    } ?: ""
                                    Text(
                                        text = timeString,
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = latestMsg?.messageText ?: "Open thread conversation...",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.Gray
                                )

                                // Attached context item if any
                                latestMsg?.productContextTitle?.let { pTitle ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(Color(0xFFF3F4F6), shape = RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Filled.Link, "Link context tag icon", tint = Color.Gray, modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = pTitle,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.DarkGray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ACTIVE CHAT SCROLL SCREEN OVERLAY
@Composable
fun ActiveChatScreen(
    supplierName: String,
    viewModel: TradeViewModel,
    onClose: () -> Unit
) {
    val msgs by viewModel.supplierMessages.collectAsState()
    var inputQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll to latest message on trigger
    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) {
            listState.animateScrollToItem(msgs.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = false) {}
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Chat Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onClose() }, modifier = Modifier.testTag("chat_back_btn")) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Close chat")
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = supplierName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(TrustGreen, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Factory Live Agent",
                                fontSize = 11.sp,
                                color = TrustGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.Shield, "SSL seal", tint = TrustGreen, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Trade Assurance", color = Color(0xFF047857), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Message list container
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 14.dp)
            ) {
                items(msgs) { msg ->
                    val isSupplier = msg.isFromSupplier
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isSupplier) Alignment.Start else Alignment.End
                    ) {
                        // Product Context Card if attached
                        if (msg.productContextTitle != null && isSupplier && msgs.indexOf(msg) == 0) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .padding(bottom = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6))
                            ) {
                                Row(modifier = Modifier.padding(8.dp)) {
                                    Icon(Icons.Filled.Link, "Attached icon", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(msg.productContextTitle, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        msg.productContextPrice?.let { Text(it, fontSize = 9.sp, color = Color.Gray) }
                                    }
                                }
                            }
                        }

                        // Message text bubble
                        Card(
                            shape = RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 12.dp,
                                bottomStart = if (isSupplier) 2.dp else 12.dp,
                                bottomEnd = if (isSupplier) 12.dp else 2.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSupplier) Color(0xFFE5E7EB) else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = msg.messageText,
                                fontSize = 13.sp,
                                color = if (isSupplier) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            )
                        }

                        // Timestamp tag
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.US).format(Date(msg.timestamp)),
                            fontSize = 10.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                        )
                    }
                }
            }

            // Bottom Input Controls Row
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearFocusOnKeyboardDismiss(),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        placeholder = { Text("Ask about custom logo, discounts, MOQ...", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            keyboardType = KeyboardType.Text
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_text"),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF3F4F6),
                            unfocusedContainerColor = Color(0xFFF3F4F6),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Solid send message trigger
                    IconButton(
                        onClick = {
                            if (inputQuery.trim().isNotEmpty()) {
                                viewModel.sendMessageToActiveSupplier(inputQuery)
                                inputQuery = ""
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                            .size(42.dp)
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "Send message",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// LOGISTICS TRACKING & BILLS TAB SCREEN
@Composable
fun LogisticsOrdersScreen(viewModel: TradeViewModel) {
    val orders by viewModel.orders.collectAsState()
    val selectedId by viewModel.selectedOrderLogisticsId.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (selectedId == null) {
            Text(
                text = "Freight Logistics & Cargo",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Tracking high-volume factory dispatch codes of secure escrows",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (orders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.LocalShipping,
                            contentDescription = "Empty Logistics logo",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Shipments Active",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Go to the storefront and complete safe Trade Assurance escrows to begin shipping.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .testTag("orders_list")
                ) {
                    items(orders) { order ->
                        ActiveOrderCard(order = order, onClick = { viewModel.viewLogistics(order.id) })
                    }
                }
            }
        } else {
            // DETAILED ACTIVE LOGISTICS MAP timelining
            val order = orders.find { it.id == selectedId }
            if (order != null) {
                LogisticsDetailTimelineView(
                    order = order,
                    viewModel = viewModel,
                    onBack = { viewModel.closeLogisticsDetails() }
                )
            }
        }
    }
}

@Composable
fun ActiveOrderCard(order: OrderEntity, onClick: () -> Unit) {
    val progressPercent = when (order.logisticsStatus) {
        "ORDER_PLACED" -> 0.15f
        "PROCESSING" -> 0.35f
        "SHIPPED" -> 0.55f
        "IN_TRANSIT" -> 0.75f
        "OUT_FOR_DELIVERY" -> 0.90f
        "DELIVERED" -> 1.0f
        "ESCROW_RELEASED" -> 1.0f
        else -> 0.05f
    }

    val displayStatus = when (order.logisticsStatus) {
        "ORDER_PLACED" -> "Contract Secured Escrow"
        "PROCESSING" -> "Cargo Packaging at Factory"
        "SHIPPED" -> "Sea Freight Dispatched (FOB)"
        "IN_TRANSIT" -> "Port Customs Screening"
        "OUT_FOR_DELIVERY" -> "Class-8 Trucks Dispatch"
        "DELIVERED" -> "Arrived destination portal"
        "ESCROW_RELEASED" -> "Escrow Discharged (Complete)"
        else -> order.logisticsStatus
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("order_item_${order.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Card(
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    AsyncImage(
                        model = order.productImageUrl,
                        contentDescription = order.productTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = order.trackingNumber,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", order.totalAmount)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = order.productTitle,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(10.dp))

            // Logistics Status Text and Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (order.logisticsStatus == "DELIVERED" || order.logisticsStatus == "ESCROW_RELEASED") Icons.Filled.CheckCircle else Icons.Filled.LocalShipping,
                        contentDescription = "Shipping icon",
                        tint = if (order.logisticsStatus == "DELIVERED" || order.logisticsStatus == "ESCROW_RELEASED") TrustGreen else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = displayStatus,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (order.logisticsStatus == "DELIVERED" || order.logisticsStatus == "ESCROW_RELEASED") TrustGreen else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${(progressPercent * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = progressPercent,
                color = if (order.logisticsStatus == "DELIVERED" || order.logisticsStatus == "ESCROW_RELEASED") TrustGreen else MaterialTheme.colorScheme.primary,
                trackColor = Color(0xFFE5E7EB),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
            )
        }
    }
}

// LOGISTICS DETAILED TIMELINE MAP VIEW WITH simulator controls
@Composable
fun LogisticsDetailTimelineView(
    order: OrderEntity,
    viewModel: TradeViewModel,
    onBack: () -> Unit
) {
    val historyLogs = viewModel.getLogisticsHistory(order.logisticsHistoryJson)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Back toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onBack() }, modifier = Modifier.testTag("logistics_back")) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back back logistics")
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = order.trackingNumber,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Text(
                    text = "B2B Logistics Ledger",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Basic bill details
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Card(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    AsyncImage(
                        model = order.productImageUrl,
                        contentDescription = order.productTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(order.productTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Escrow Account Status: ${order.paymentStatus}", fontSize = 11.sp, color = Color(0xFF0D9488), fontWeight = FontWeight.SemiBold)
                    Text("Quantity Ordered: ${order.quantity} units", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic visual progress tracker logs
        Text(
            text = "Tracking Milestones & Escrows",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Waypoints listing
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp)
        ) {
            historyLogs.forEachIndexed { index, log ->
                val isLatest = index == historyLogs.size - 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    // Dot and track vertical line
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    if (isLatest) MaterialTheme.colorScheme.primary else TrustGreen,
                                    shape = CircleShape
                                )
                                .border(
                                    width = 3.dp,
                                    color = if (isLatest) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent,
                                    shape = CircleShape
                                )
                        )

                        // Vertical connector line
                        if (index < historyLogs.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(68.dp)
                                    .background(TrustGreen)
                            )
                        } else if (order.logisticsStatus != "ESCROW_RELEASED") {
                            // Dotted or grey vertical pending line
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(68.dp)
                                    .background(Color.LightGray)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Log descriptions Text
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val niceTitle = when (log.status) {
                                "ORDER_PLACED" -> "Contract Escrow Secured"
                                "PROCESSING" -> "Commercial Inspections / Packing"
                                "SHIPPED" -> "Cargo Freighter Dispatched"
                                "IN_TRANSIT" -> "Port Gateway Clearance"
                                "OUT_FOR_DELIVERY" -> "Class-8 Freight Trucking Routing"
                                "DELIVERED" -> "Arrived Destination Dock"
                                "ESCROW_RELEASED" -> "Escrow Discharged & Complete"
                                else -> log.status
                            }
                            Text(
                                text = niceTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLatest) MaterialTheme.colorScheme.primary else Color.Black
                            )

                            val logTime = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(log.timestamp))
                            Text(
                                text = logTime,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        Text(
                            text = log.location,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = log.description,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = if (isLatest) Color.Black else Color.Gray
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SIMULATION DRIVER CONTROL
        if (order.logisticsStatus != "ESCROW_RELEASED") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Real-Time Logistics Simulator Control",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Because real global shipping takes weeks, click this simulation developer button to fast-forward logs and verify real-time status banners.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val actionLabel = when (order.logisticsStatus) {
                        "ORDER_PLACED" -> "Generate Supplier Seal (Processing)"
                        "PROCESSING" -> "Dispatch Port freighter (Shipped)"
                        "SHIPPED" -> "Arrive Port Customs (Transit)"
                        "IN_TRANSIT" -> "Load Local Freight Hauler (Delivery)"
                        "OUT_FOR_DELIVERY" -> "Complete Wharf Arrival (Delivered)"
                        "DELIVERED" -> "Verify & Discharge Funds (Escrow Release)"
                        else -> "Transit Completed"
                    }

                    Button(
                        onClick = { viewModel.speedrunTransitTransition(order.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_speedrun_logistics"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.FastForward, contentDescription = "Fast forward cargo status logo")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = TrustGreen.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.VerifiedSecured, "Certified transaction secure seal", tint = TrustGreen)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Import Contract Complete",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857)
                        )
                        Text(
                            text = "Cargo confirmed at home ports, and bank released funds from Escrow to manufacturer ledger successfully.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

private val Icons.Filled.VerifiedSecured: ImageVector
    get() = Icons.Filled.OfflinePin

// IN-APP SYSTEM PUSH OVERLAY TOAST
@Composable
fun InAppNotificationBanner(
    notification: NotificationEntity,
    onDismiss: () -> Unit,
    onClick: () -> Unit
) {
    val bannerBg = when (notification.type) {
        "LOGISTICS" -> Color(0xFFE0F2FE)
        "TRADE_ASSURANCE" -> Color(0xFFFEF3C7)
        "MESSAGE" -> Color(0xFFF3E8FF)
        else -> Color(0xFFF3F4F6)
    }

    val iconValue = when (notification.type) {
        "LOGISTICS" -> Icons.Filled.LocalShipping
        "TRADE_ASSURANCE" -> Icons.Filled.Lock
        "MESSAGE" -> Icons.Filled.QuestionAnswer
        else -> Icons.Filled.Notifications
    }

    val iconColor = when (notification.type) {
        "LOGISTICS" -> Color(0xFF0284C7)
        "TRADE_ASSURANCE" -> Color(0xFFD97706)
        "MESSAGE" -> Color(0xFF7E22CE)
        else -> Color.Black
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(12.dp, shape = RoundedCornerShape(16.dp))
            .testTag("in_app_banner_container"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bannerBg),
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconValue,
                    contentDescription = "Alert notification graphic logo",
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = notification.body,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.DarkGray,
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(onClick = { onDismiss() }) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Close push banner",
                    tint = Color.DarkGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// NOTIFICATION LOG ALERTS TAB SCREEN
@Composable
fun NotificationsAlertsScreen(viewModel: TradeViewModel) {
    val alerts by viewModel.notifications.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "System Trade Alerts",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Audit trail of escrow locks, contracts, and shipping",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            if (alerts.any { !it.isRead }) {
                TextButton(
                    onClick = { viewModel.markAllNotificationsAsRead() },
                    modifier = Modifier.testTag("btn_mark_all_read")
                ) {
                    Text("Clear All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (alerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsNone,
                        contentDescription = "Empty notifications",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Your Trade Archive is Empty",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Updates on bank secures, supplier text, and logistics map reports catalog here.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("notifications_list")
            ) {
                items(alerts) { alert ->
                    val colorBanner = when (alert.type) {
                        "LOGISTICS" -> Color(0xFFF0FDF4)
                        "TRADE_ASSURANCE" -> Color(0xFFFFFBEB)
                        "MESSAGE" -> Color(0xFFFAF5FF)
                        else -> Color(0xFFF9FAFB)
                    }

                    val colorLine = when (alert.type) {
                        "LOGISTICS" -> TrustGreen
                        "TRADE_ASSURANCE" -> Color(0xFFF59E0B)
                        "MESSAGE" -> Color(0xFF8B5CF6)
                        else -> Color.LightGray
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notification_item_${alert.id}"),
                        colors = CardDefaults.cardColors(containerColor = colorBanner),
                        border = BorderStroke(1.dp, colorLine.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(40.dp)
                                    .background(colorLine, shape = CircleShape)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = alert.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    val alertTimeString = SimpleDateFormat("HH:mm", Locale.US).format(Date(alert.timestamp))
                                    Text(
                                        text = alertTimeString,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = alert.body,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = Color.DarkGray
                                )
                            }

                            IconButton(
                                onClick = { viewModel.deleteNotification(alert.id) },
                                modifier = Modifier.testTag("delete_notif_${alert.id}")
                            ) {
                                Icon(Icons.Filled.DeleteOutline, "Remove alert line", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// HELPER: Close soft keyboards on focus adjustments
@Composable
fun Modifier.clearFocusOnKeyboardDismiss(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboardWidth = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(keyboardWidth) {
        if (keyboardWidth == 0.dp) {
            focusManager.clearFocus()
        }
    }
    return this
}
