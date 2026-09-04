package ir.nv.navigation.entitlement

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BillingState(
    val connecting: Boolean = true,
    val purchased: Boolean = false,
    val formattedPrice: String? = null,
    val message: String? = null
)

class PlayBillingManager(context: Context) : PurchasesUpdatedListener {
    private val mutableState = MutableStateFlow(BillingState())
    val state: StateFlow<BillingState> = mutableState.asStateFlow()
    private var productDetails: ProductDetails? = null
    private var closed = false
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    init {
        connect()
    }

    fun launchPurchase(activity: Activity) {
        val product = productDetails
        if (!client.isReady || product == null) {
            mutableState.value = mutableState.value.copy(message = "فروشگاه Google Play آماده نیست")
            if (!client.isReady) connect()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .build()
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            mutableState.value = mutableState.value.copy(message = billingMessage(result))
        }
    }

    fun close() {
        closed = true
        client.endConnection()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            processPurchases(purchases.orEmpty())
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            mutableState.value = mutableState.value.copy(message = billingMessage(result))
        }
    }

    private fun connect() {
        if (closed || client.isReady || client.connectionState == BillingClient.ConnectionState.CONNECTING) return
        mutableState.value = mutableState.value.copy(connecting = true, message = null)
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    restorePurchase()
                } else {
                    mutableState.value = mutableState.value.copy(
                        connecting = false,
                        message = billingMessage(result)
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                mutableState.value = mutableState.value.copy(
                    connecting = false,
                    message = "ارتباط با فروشگاه قطع شد"
                )
            }
        })
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        ) { result, products ->
            productDetails = products.firstOrNull()
            mutableState.value = mutableState.value.copy(
                connecting = false,
                formattedPrice = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice,
                message = if (result.responseCode == BillingClient.BillingResponseCode.OK && productDetails == null) {
                    "محصول nv_full_version هنوز در Play Console ساخته نشده است"
                } else if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    mutableState.value.message
                } else {
                    billingMessage(result)
                }
            )
        }
    }

    private fun restorePurchase() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val purchase = purchases.firstOrNull {
            PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        } ?: return
        if (!purchase.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    mutableState.value = mutableState.value.copy(purchased = true, message = null)
                } else {
                    mutableState.value = mutableState.value.copy(message = billingMessage(result))
                }
            }
        } else {
            mutableState.value = mutableState.value.copy(purchased = true, message = null)
        }
    }

    private fun billingMessage(result: BillingResult): String =
        result.debugMessage.takeIf { it.isNotBlank() } ?: "خطای فروشگاه: ${result.responseCode}"

    private companion object {
        const val PRODUCT_ID = "nv_full_version"
    }
}
