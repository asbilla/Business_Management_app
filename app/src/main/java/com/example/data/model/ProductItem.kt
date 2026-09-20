package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ProductItem(
    val name: String,
    val price: Double,
    val category: String = ""
)
