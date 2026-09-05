package com.rokiddemo.phone.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Lists every non-loopback IPv4 address the phone currently owns, labelled by
     * interface name. When the phone is a Wi-Fi hotspot, the address the glasses
     * should target usually shows up here as something like:
     *   ap0: 192.168.43.1   (or  swlan0: 192.168.x.1)
     * When both are on the same Wi-Fi, use the wlan0 address instead.
     */
    fun localIpv4Addresses(): List<String> {
        val out = ArrayList<String>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        out.add("${nif.name}: ${addr.hostAddress}")
                    }
                }
            }
        } catch (e: Exception) {
            out.add("(could not read interfaces: ${e.message})")
        }
        if (out.isEmpty()) out.add("(no active IPv4 address)")
        return out
    }
}
