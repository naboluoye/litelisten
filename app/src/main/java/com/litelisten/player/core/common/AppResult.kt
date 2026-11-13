package com.litelisten.player.core.common

import com.litelisten.player.core.model.SourceError

/** 项目内的结构化结果类型；冻结契约仍使用 Kotlin Result，用 toAppResult 桥接。 */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>
    data class Failure(val error: SourceError) : AppResult<Nothing>
}

fun <T> Result<T>.toAppResult(
    mapError: (Throwable) -> SourceError = { SourceError.Unknown(it) },
): AppResult<T> = fold(
    onSuccess = { AppResult.Success(it) },
    onFailure = { AppResult.Failure(mapError(it)) },
)
