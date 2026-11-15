package xyz.robinjoon.adocs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HostTest {
    @Test
    fun `host syntax failures preserve their cause behind a safe CLI error`() {
        val failure = assertThrows(CliFailure::class.java) { Host("https://bad host") }
        assertEquals("invalid_input", failure.code)
        assertEquals("host must be a valid HTTPS origin", failure.fix)
        org.junit.jupiter.api.Assertions
            .assertInstanceOf(java.net.URISyntaxException::class.java, failure.cause)
    }

    @Test
    fun `credentials use normalized instance origin and distinct host keys`() {
        assertEquals(Host("https://docs.example").key, Host("https://docs.example/").key)
        assertNotEquals(Host("https://docs.example").key, Host("https://other.example").key)
        assertEquals("https://docs.example/oauth/clients/adocs", Host("https://docs.example").clientId)
        assertEquals("https://docs.example/mcp", Host("https://docs.example").resource)
    }

    @Test
    fun `credentials query fragments paths and insecure hosts are rejected`() {
        listOf(
            "http://docs.example",
            "https://user:password@docs.example",
            "https://docs.example/path",
            "https://docs.example?x=y",
            "https://docs.example#fragment",
        ).forEach { value -> assertThrows(IllegalArgumentException::class.java) { Host(value) } }
    }
}
