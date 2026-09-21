package com.example.data.repository

import com.example.data.local.ProductDao
import com.example.data.local.ProductEntity
import com.example.data.pref.AppPreferences
import java.util.UUID

/**
 * Deterministic Product Migration.
 *
 * Ensures that legacy products migrated from SharedPreferences or presets
 * receive stable, deterministic UUIDs (RFC 4122 v3 Name-Based UUID).
 *
 * Prevents duplicate product identities when synchronizing across Android and Windows devices.
 */
object DeterministicProductMigration {

    private const val PRODUCT_NAMESPACE = "com.mybusiness.product"

    /**
     * Generates a stable, reproducible UUID for a product based on its category and name.
     * The generated UUID is identical regardless of which platform or device performs the generation.
     */
    fun generateDeterministicProductUuid(name: String, category: String = ""): String {
        val normalizedName = name.trim().lowercase()
        val normalizedCategory = category.trim().lowercase()
        val key = "$PRODUCT_NAMESPACE:$normalizedCategory:$normalizedName"
        return UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
    }

    /**
     * One-time, idempotent migration of legacy SharedPreferences products into Room ProductEntity.
     * After this migration, Room is the ONLY authoritative source of product data.
     */
    suspend fun migrateLegacyProductsIfNecessary(
        productDao: ProductDao,
        preferences: AppPreferences,
        deviceId: String
    ): Int {
        val activeCount = productDao.getActiveCount()
        if (activeCount > 0) {
            // Already initialized in Room
            return 0
        }

        val legacyProducts = preferences.getCachedProducts()
        var migratedCount = 0
        val now = System.currentTimeMillis()

        for (item in legacyProducts) {
            if (item.name.isBlank()) continue
            val deterministicUuid = generateDeterministicProductUuid(item.name, item.category)
            val existing = productDao.getProductByUuid(deterministicUuid) ?: productDao.getProductByName(item.name)

            if (existing == null) {
                productDao.insertProduct(
                    ProductEntity(
                        uuid = deterministicUuid,
                        name = item.name.trim(),
                        description = "",
                        category = item.category.trim(),
                        price = item.price,
                        type = "Service",
                        active = true,
                        createdAt = now,
                        updatedAt = now,
                        version = 1L,
                        deviceId = deviceId
                    )
                )
                migratedCount++
            }
        }
        return migratedCount
    }
}
