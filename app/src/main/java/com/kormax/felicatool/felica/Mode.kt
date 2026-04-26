package com.kormax.felicatool.felica

/** Per-system card operating mode with authentication subtype tracking. */
sealed class Mode(val modeNumber: Int) {
    data object Mode0 : Mode(0) {
        override fun toString(): String = "Mode0"
    }

    sealed class Mode1 : Mode(1) {
        data object Des : Mode1() {
            override fun toString(): String = "Mode1.Des"
        }

        data object Aes : Mode1() {
            override fun toString(): String = "Mode1.Aes"
        }

        data object AesMac : Mode1() {
            override fun toString(): String = "Mode1.AesMac"
        }
    }

    sealed class Mode2 : Mode(2) {
        data object Des : Mode2() {
            override fun toString(): String = "Mode2.Des"
        }

        data object Aes : Mode2() {
            override fun toString(): String = "Mode2.Aes"
        }
    }

    sealed class Mode3 : Mode(3) {
        data object Des : Mode3() {
            override fun toString(): String = "Mode3.Des"
        }

        data object Aes : Mode3() {
            override fun toString(): String = "Mode3.Aes"
        }
    }
}
