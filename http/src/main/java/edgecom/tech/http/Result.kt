package edgecom.tech.http

/**
 * 请求的结果
 */
data class Result<T>(val data: T?, val error: Exception?)