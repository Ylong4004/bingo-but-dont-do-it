package me.jfenn.bingo.integrations.permissions

class PermissionKey(
    val permission: String,
    val default: PermissionDefault,
)

enum class PermissionDefault {
    ALLOW,
    OPERATORS,
    DENY,
}
