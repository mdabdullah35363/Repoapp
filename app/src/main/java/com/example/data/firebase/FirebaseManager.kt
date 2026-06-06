package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.example.data.model.OrderEntity
import com.example.data.model.MessageEntity
import com.example.data.model.NotificationEntity
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    
    var isFirebaseInitialized = false
        private set

    private var database: FirebaseDatabase? = null
    private var firestore: FirebaseFirestore? = null

    fun initialize(context: Context) {
        val apiKey = try { BuildConfig.FIREBASE_API_KEY } catch (e: Exception) { "" }
        val projectId = try { BuildConfig.FIREBASE_PROJECT_ID } catch (e: Exception) { "" }
        val databaseUrl = try { BuildConfig.FIREBASE_DATABASE_URL } catch (e: Exception) { "" }
        val appId = try { BuildConfig.FIREBASE_APP_ID } catch (e: Exception) { "" }

        if (apiKey.isNullOrEmpty() || projectId.isNullOrEmpty() || appId.isNullOrEmpty() ||
            apiKey == "EMPTY" || projectId == "EMPTY" || appId == "EMPTY"
        ) {
            Log.w(TAG, "Firebase credentials not fully supplied or set to default EMPTY stubs. Local Offline Room DB is active.")
            isFirebaseInitialized = false
            return
        }

        try {
            // Check if App already initialized
            val existingApp = FirebaseApp.getApps(context).firstOrNull()
            val app = if (existingApp == null) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setProjectId(projectId)
                    .setApplicationId(appId)
                    .apply {
                        if (!databaseUrl.isNullOrEmpty()) {
                            setDatabaseUrl(databaseUrl)
                        }
                    }
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
            } else {
                existingApp
            }

            database = try { FirebaseDatabase.getInstance() } catch (e: Exception) { null }
            firestore = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
            
            isFirebaseInitialized = true
            Log.i(TAG, "Successfully initialized Google Firebase Dynamic Sync with cloud backend.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing programmatic Firebase: ${e.message}", e)
            isFirebaseInitialized = false
        }
    }

    // High performance sync methods to push wholesale operations up to Realtime DB/Firestore 
    fun syncOrder(order: OrderEntity) {
        if (!isFirebaseInitialized) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                database?.getReference("orders")?.child(order.id.toString())?.setValue(order)
                firestore?.collection("orders")?.document(order.id.toString())?.set(order)
                Log.d(TAG, "Synced Order #${order.id} with Firebase cloud backend.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed syncing Order to Firebase cloud: ${e.message}")
            }
        }
    }

    fun syncMessage(message: MessageEntity) {
        if (!isFirebaseInitialized) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                database?.getReference("messages")?.child(message.id.toString())?.setValue(message)
                firestore?.collection("messages")?.document(message.id.toString())?.set(message)
                Log.d(TAG, "Synced Message with Firebase cloud backend.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed syncing Message to Firebase cloud: ${e.message}")
            }
        }
    }

    fun syncNotification(notification: NotificationEntity) {
        if (!isFirebaseInitialized) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                database?.getReference("notifications")?.child(notification.id.toString())?.setValue(notification)
                firestore?.collection("notifications")?.document(notification.id.toString())?.set(notification)
                Log.d(TAG, "Synced Notification with Firebase cloud backend.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed syncing Notification to Firebase cloud: ${e.message}")
            }
        }
    }
}
