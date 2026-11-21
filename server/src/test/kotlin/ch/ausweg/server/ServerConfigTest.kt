package ch.ausweg.server

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ServerConfigTest {
    private val dir = Files.createTempDirectory("config")
    private val dotEnv = dir.resolve(".env")
    private val props = dir.resolve("local.properties")
    private val missing = Path.of("does-not-exist")

    private fun key(
        env: Map<String, String> = emptyMap(),
        dotEnvFile: Path = missing,
        propsFile: Path = missing,
    ) = ServerConfig.fromEnv(env, localProperties = propsFile, dotEnv = dotEnvFile).ojpKey?.value

    @Test
    fun `env token wins, then env key, then dot env, then local properties`() {
        dotEnv.writeText("OJP_API_TOKEN=\"from-dotenv\"\n")
        props.writeText("ojp.apiKey=from-props\n")
        assertEquals("a", key(mapOf("OJP_API_TOKEN" to "a", "OJP_API_KEY" to "b"), dotEnv, props))
        assertEquals("b", key(mapOf("OJP_API_KEY" to "b"), dotEnv, props))
        assertEquals("from-dotenv", key(dotEnvFile = dotEnv, propsFile = props))
        assertEquals("from-props", key(propsFile = props))
        assertNull(key())
    }

    @Test
    fun `key never shows in toString`() {
        val config = ServerConfig.fromEnv(mapOf("OJP_API_TOKEN" to "secret-value"), missing, missing)
        assertFalse(config.toString().contains("secret-value"))
    }
}
