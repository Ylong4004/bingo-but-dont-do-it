package me.jfenn.bingo.integrations.permissions

import me.jfenn.bingo.platform.IModEnvironment

interface IPermissionsApiFactory {
    fun create(environment: IModEnvironment): IPermissionsApi
}