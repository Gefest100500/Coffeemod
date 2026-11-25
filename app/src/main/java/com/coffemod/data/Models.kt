package com.coffemod.data

data class Group(
    val id: Long,
    val name: String,
    val sort: Int,
)

data class Product(
    val id: Long = 0,
    val name: String,
    val groupId: Long?,
    val unit: String,
    val minStock: Double,
    val stock: Double,
    val expiresOn: String?,
    val note: String,
    val archived: Boolean = false,
)

data class BarcodeRow(
    val id: Long,
    val productId: Long,
    val code: String,
)

data class Movement(
    val id: Long = 0,
    val productId: Long,
    val kind: String,
    val qty: Double,
    val onDate: String,
    val note: String = "",
)

data class MovementRow(
    val id: Long,
    val productId: Long,
    val productName: String,
    val unit: String,
    val kind: String,
    val qty: Double,
    val onDate: String,
    val note: String,
)

data class SessionRow(
    val id: Long,
    val startedAt: String,
    val status: String,
)

data class SessionItemRow(
    val productId: Long,
    val name: String,
    val unit: String,
    val expected: Double,
    val actual: Double,
)
