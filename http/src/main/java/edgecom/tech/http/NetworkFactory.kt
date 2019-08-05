package edgecom.tech.http

import retrofit2.Retrofit

/**
 * 创建网络请求相关的组件
 */
interface NetworkFactory {

    fun createRetrofit(): Retrofit
}