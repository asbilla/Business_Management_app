package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE deletedAt IS NULL ORDER BY category ASC, name ASC")
    fun getAllActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE deletedAt IS NULL ORDER BY category ASC, name ASC")
    suspend fun getAllActiveProductsSync(): List<ProductEntity>

    @Query("SELECT * FROM products ORDER BY id ASC")
    suspend fun getAllProductsSync(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE updatedAt >= :sinceTimestamp")
    suspend fun getProductsModifiedSince(sinceTimestamp: Long): List<ProductEntity>

    @Query("SELECT * FROM products WHERE uuid = :uuid LIMIT 1")
    suspend fun getProductByUuid(uuid: String): ProductEntity?

    @Query("SELECT * FROM products WHERE LOWER(name) = LOWER(:name) AND deletedAt IS NULL LIMIT 1")
    suspend fun getProductByName(name: String): ProductEntity?

    @Query("SELECT COUNT(*) FROM products WHERE deletedAt IS NULL")
    suspend fun getActiveCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>): List<Long>

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("UPDATE products SET deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1 WHERE uuid = :uuid")
    suspend fun softDeleteByUuid(uuid: String, deletedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM products WHERE uuid = :uuid")
    suspend fun deleteByUuidPermanently(uuid: String)

    @Query("DELETE FROM products")
    suspend fun clearAll()
}
