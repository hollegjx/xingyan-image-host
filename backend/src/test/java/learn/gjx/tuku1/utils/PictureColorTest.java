package learn.gjx.tuku1.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PictureColorTest {
    @Test void normalizesCosNumericAndCanonicalColors() {
        assertEquals("#0080e0", PictureColor.normalize("0x080e0"));
        assertEquals("#000000", PictureColor.normalize("0x0"));
        assertEquals("#aabbcc", PictureColor.normalize(" #AaBbCc "));
        assertEquals("#ffffff", PictureColor.normalize("0XFFFFFF"));
    }
    @Test void missingAndInvalidMetadataRemainUnknownNotBlack() {
        for (String value : new String[]{null, "", "#abc", "red", "#12345678", "0x1000000", "-1", "0xff;bad"}) {
            assertNull(PictureColor.normalize(value), value);
        }
    }
    @Test void rgbDistanceIsSymmetricAndOrdersNeighbors() {
        assertEquals(0, PictureColor.distanceSquared(0xff0000, 0xff0000));
        assertEquals(3 * 255 * 255, PictureColor.distanceSquared(0, 0xffffff));
        assertEquals(PictureColor.distanceSquared(0x123456, 0xabcdef),
                PictureColor.distanceSquared(0xabcdef, 0x123456));
        assertTrue(PictureColor.distanceSquared(0xff0000, 0xfe0000) < PictureColor.distanceSquared(0xff0000, 0x00ff00));
    }
}
