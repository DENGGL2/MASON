package com.denggl2.mason.sync.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.denggl2.mason.protocol.DeviceKeyAlgorithm
import java.security.KeyFactory
import java.security.KeyStore
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDeviceIdentityStoreTest {
    @Test
    fun identityIsStablePrivateKeyIsNonExportableAndSignatureVerifies() {
        val alias = "mason_test_device_identity_${UUID.randomUUID()}"
        val store = AndroidDeviceIdentityStore(alias)
        try {
            val first = store.getOrCreateIdentity()
            val second = store.getOrCreateIdentity()
            val payload = "mason-auth-challenge"
            val signature = Base64.getDecoder().decode(store.sign(payload))
            val publicKey = KeyFactory.getInstance("EC").generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(first.publicKey)),
            )

            assertEquals(DeviceKeyAlgorithm.ECDSA_P256_SHA256, first.keyAlgorithm)
            assertEquals(first, second)
            assertTrue(Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(payload.toByteArray(Charsets.UTF_8))
                verify(signature)
            })

            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
            assertNull(entry.privateKey.encoded)
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias(alias)) deleteEntry(alias)
            }
        }
    }
}
