package edgecom.tech.http

import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Retrofit
import java.io.IOException
import java.lang.reflect.Method

/**
 * 网络请求类
 */
open class Request<T>(private val factory: NetworkFactory) {

    /**
     * GET请求
     * @param command: 请求命令字，格式：service#method
     * @param action:  处理网络请求的结果，在主线程中调用
     * @param queries: 请求参数
     * @param before:  在网络请求之前做一些事
     * @param after:   在网络请求完成之后做一些事
     */
    fun get(
        command: String, queries: Map<String, String> = mapOf(), action: (Result<T>?) -> Unit,
        before: () -> Unit = {}, after: () -> Unit = {}
    ) {
        val cmd = command.split("#")
        if (cmd.size == 2) {

            before()    //网络请求之前做一些事情

            //启动协程
            GlobalScope.launch(Dispatchers.IO) {
                val d = async {
                    var result: Result<T>? = null
                    //1、创建retrofit对象
                    val retrofit = factory.createRetrofit()

                    //2、创建方法
                    val ret =
                        Cache.getMethod(cmd[0], cmd[1], retrofit)?.invoke(Cache.getServer(command), queries) as? Call<T>

                    //3、发起网络请求
                    try {
                        ret?.execute()?.run {
                            body()?.let {
                                result = Result(it, null)
                            }

                            after() //网络请求之后做一些事情
                        }
                    } catch (e: Exception) {
                        result = Result(null, e)
                    }
                    result
                }
                //在主线程中回调处理方法
                withContext(Dispatchers.Main) { action(d.await()) }
            }
        }
    }

    /**
     * POST请求
     * @param command: 请求命令字，格式：service#method
     * @param action:  处理网络请求的结果，在主线程中调用
     * @param queries: post请求的查询参数
     * @param body:    post请求的body
     */
    fun post(
        command: String, queries: Map<String, String>? = mapOf(), body: Any? = "{}",
        action: (Result<T>?) -> Unit, before: () -> Unit = {}, after: () -> Unit = {}
    ) {
        val cmd = command.split("#")
        if (cmd.size == 2) {

            before() //网络请求之前做些事情

            //启动协程
            GlobalScope.launch(Dispatchers.IO) {
                val d = async {
                    var result: Result<T>? = null

                    //1、创建retrofit对象
                    val retrofit = factory.createRetrofit()

                    val ret = Cache.getMethod(cmd[0], cmd[1], retrofit)?.invoke(
                        Cache.getServer(command),
                        queries,
                        body
                    ) as? Call<T>

                    //3、发起网络请求
                    try {
                        ret?.execute()?.run {
                            body()?.let {
                                result = Result(it, null)
                            }
                        }

                        after() //网络请求之后做些事情

                    } catch (e: IOException) {  //出现异常
                        result = Result(null, e)
                    }
                    result
                }

                withContext(Dispatchers.Main) { action(d.await()) }
            }
        }
    }

    private object Cache {

        var server: MutableMap<String, Any?> = mutableMapOf()

        var methods: MutableMap<String, Method?> = mutableMapOf()

        fun getMethod(serverStr: String, method: String, retrofit: Retrofit?): Method? {
            val command = "$serverStr#$method"
            if (!methods.containsKey(command)) {    //缓存中没有该命令字对应的方法，需要解析
                val svr = retrofit?.create(Class.forName(serverStr))
                val cls = svr!!::class.java
                methods[command] = cls.methods.first { it.name == method }
                server[command] = svr
            }
            return methods[command]
        }

        fun getServer(command: String): Any? = server[command]
    }
}