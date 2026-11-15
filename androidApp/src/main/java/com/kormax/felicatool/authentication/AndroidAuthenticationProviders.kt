package com.kormax.felicatool.authentication

import android.content.Context
import java.util.UUID

internal object AndroidAuthenticationProviders {
    private var instance: AuthenticationProviders? = null

    @Synchronized
    fun instance(context: Context): AuthenticationProviders =
        instance
            ?: AuthenticationProviders(
                    storage =
                        AndroidSecureAuthenticationProviderStorage(context.applicationContext),
                    keyStore = AndroidProtectedDesKeyStore(context.applicationContext),
                    newId = { UUID.randomUUID().toString() },
                )
                .also { instance = it }
}
