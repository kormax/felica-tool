package com.kormax.felicatool.service

internal class StepPreconditionNotMet(message: String) : RuntimeException(message)

internal class StepBehaviorUnexpected(message: String) : RuntimeException(message)
