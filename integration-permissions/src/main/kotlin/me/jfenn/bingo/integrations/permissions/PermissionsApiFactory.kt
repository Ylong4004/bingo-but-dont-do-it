package me.jfenn.bingo.integrations.permissions

import me.jfenn.bingo.platform.IModEnvironment

class PermissionsApiFactory : IPermissionsApiFactory {
    override fun create(environment: IModEnvironment): IPermissionsApi {
        return when {
            environment.isModLoaded("fabric-permissions-api-v0") -> PermissionsApi()
            else -> FallbackPermissionsApi()
        }
    }
}