package com.github.nagyesta.filebarj.job.util;

import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import com.github.nagyesta.filebarj.job.TempFileAwareTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class KeyStoreUtilTest extends TempFileAwareTest {

    @Test
    void testReadPrivateKeyShouldReadBackPrivateKeyWhenItWasPreviouslyWritten() {
        //given
        final var store = testDataRoot.resolve("key-store.p12");
        final var password1 = new char[]{'a', 'b', 'c', '1'};
        final var password2 = new char[]{'a', 'b', 'c', '2'};
        final var alias = "alias";
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        KeyStoreUtil.writeKey(store, alias, keyPair, password1, password2);

        //when
        final var actual = KeyStoreUtil.readPrivateKey(store, alias, password1, password2);

        //then
        Assertions.assertEquals(keyPair.getPrivate(), actual);
    }

    @Test
    void testReadPublicKeyShouldReadBackPublicKeyWhenItWasPreviouslyWritten() {
        //given
        final var store = testDataRoot.resolve("key-store.p12");
        final var password1 = new char[]{'a', 'b', 'c', '1'};
        final var password2 = new char[]{'a', 'b', 'c', '2'};
        final var alias = "alias";
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
        KeyStoreUtil.writeKey(store, alias, keyPair, password1, password2);

        //when
        final var actual = KeyStoreUtil.readPublicKey(store, alias, password1);

        //then
        Assertions.assertEquals(keyPair.getPublic(), actual);
    }
}
