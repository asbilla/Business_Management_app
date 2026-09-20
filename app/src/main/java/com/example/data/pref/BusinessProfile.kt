package com.example.data.pref

import kotlinx.serialization.Serializable

@Serializable
data class BusinessProfile(
    val businessName: String = "",
    val abnAcn: String = "",
    val businessAddress: String = "",
    val phoneMobile: String = "",
    val email: String = ""
)
