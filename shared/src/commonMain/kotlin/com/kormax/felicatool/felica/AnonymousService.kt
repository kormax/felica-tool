package com.kormax.felicatool.felica

/** A service on a card that does not use service codes. */
object AnonymousService : Service(ServiceAttribute.RandomRwWithoutKey) {
    override fun getServiceCode(): Short = 0x0009

    override fun toString(): String = "AnonymousService"
}
