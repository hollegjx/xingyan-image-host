package learn.gjx.tuku1.controller;

import learn.gjx.tuku1.config.ImageSearchProperties;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.PrivatePictureFileManager;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PictureCapabilitiesControllerTest {
    @Test void unavailableGatesAreExplicitAndNoConfigurationSecretsAreReturned() {
        PrivatePictureFileManager storage=mock(PrivatePictureFileManager.class);
        when(storage.bucket(1L)).thenThrow(new BusinessException(ErrorCode.OPERATION_ERROR));
        ImageSearchProperties search=new ImageSearchProperties();
        PictureCapabilitiesController controller=new PictureCapabilitiesController(storage,search);
        MockHttpServletResponse response=new MockHttpServletResponse();
        Map<String,Boolean> result=controller.capabilities(response).getData();
        assertEquals(2,result.size());assertFalse(result.get("privateStorageConfigured"));assertFalse(result.get("externalImageSearchEnabled"));
        assertEquals("no-store",response.getHeader("Cache-Control"));
        doReturn("not-returned").when(storage).bucket(1L);search.setEnabled(true);
        assertTrue(controller.capabilities(response).getData().get("privateStorageConfigured"));
        assertTrue(controller.capabilities(response).getData().get("externalImageSearchEnabled"));
    }
}
