package xyz.robinjoon.agentdocs.webhook.adapter.outbound.http

import java.net.Inet4Address
import java.net.InetAddress

// The owner may point a webhook at the homelab, so private and loopback addresses are allowed. Refused are addresses no
// receiver should have: unspecified, link-local with the cloud metadata address, multicast, the IPv4 "this network" and
// broadcast ranges, and the IPv6 metadata address. Checked on the resolved addresses before every attempt.
internal object WebhookAddressPolicy {
    private val metadataV6 = InetAddress.getByName("fd00:ec2::254")

    fun refuses(address: InetAddress): Boolean {
        val first = address.address[0].toInt() and BYTE
        val reservedV4 = address is Inet4Address && (first == 0 || first >= RESERVED_FIRST)
        val unusable = address.isAnyLocalAddress || address.isLinkLocalAddress || address.isMulticastAddress
        return unusable || reservedV4 || address == metadataV6
    }

    private const val BYTE = 0xFF
    private const val RESERVED_FIRST = 240
}
