package com.jmreader.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class JmCryptoTest {

    @Test
    fun `md5 matches known vectors`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", JmCrypto.md5(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", JmCrypto.md5("abc"))
    }

    @Test
    fun `token is md5 of timestamp plus secret`() {
        assertEquals("880c64833265ad47a928afcf1b1220f5", JmCrypto.token(1700000000L))
        assertEquals("1700000000,2.1.12", JmCrypto.tokenParam(1700000000L))
    }

    @Test
    fun `decrypt payload with primary key`() {
        val plain = JmCrypto.decryptPayload("t12hiwE8OnbhuPpo/djdaHpwuxJC5Js7rojinQQXhvM=", 1700000000L)
        assertEquals("{\"name\":\"測試\",\"id\":123}", plain)
    }

    @Test
    fun `decrypt payload falls back to content key`() {
        val plain = JmCrypto.decryptPayload("zf80tL6gjBF9eGfpJAzUFQ==", 1700000000L)
        assertEquals("{\"ok\":true}", plain)
    }

    @Test
    fun `decrypt host list`() {
        val raw = "BNJwNKYyKW+qdIWbzuIOAcMO+FcU0selzWrDyiJAIJMK63leJzgHwN5asqFaWPMtX4OMs0HvxRC+Fpx3wiszbPUfLoDsvLP1Gfz/2PSnXfW20FeNltvmCuzFtYIctqT+"
        val json = Json.parseToJsonElement(JmCrypto.decryptHostList(raw)).jsonObject
        val hosts = json["jm3_Server"]!!.jsonArray
        assertEquals(2, hosts.size)
        assertEquals("a.cc", hosts[0].jsonArray[0].jsonPrimitive.content)
        assertEquals("線路1", hosts[0].jsonArray[1].jsonPrimitive.content)
    }
}
