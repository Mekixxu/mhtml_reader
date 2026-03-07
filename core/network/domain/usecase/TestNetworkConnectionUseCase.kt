package core.network.domain.usecase

import core.network.domain.model.NetworkConfig
import core.network.domain.validator.NetworkConfigValidator
import core.common.DispatcherProvider
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 测试网络连接占位（校验、DNS、端口通）。
 * v1.0解释：仅做IP解析+端口连接测试。
 */
class TestNetworkConnectionUseCase(
    private val dispatcherProvider: DispatcherProvider
) {
    sealed class TestResult {
        data class Success(val latencyMs: Long) : TestResult()
        data class Fail(val error: Throwable) : TestResult()
    }

    suspend fun test(config: NetworkConfig, timeoutMs: Int = 3000): TestResult =
        withContext(dispatcherProvider.io) {
            NetworkConfigValidator.validate(config).fold(
                onSuccess = { valid ->
                    try {
                        val start = System.nanoTime()
                        val address = InetAddress.getByName(valid.host)
                        val socket = Socket()
                        val socketAddress = InetSocketAddress(address, valid.port)
                        socket.connect(socketAddress, timeoutMs)
                        socket.close()
                        val elapsed = (System.nanoTime() - start) / 1_000_000
                        TestResult.Success(elapsed)
                    } catch (e: Exception) {
                        TestResult.Fail(e)
                    }
                },
                onFailure = { TestResult.Fail(it) }
            )
        }
}
