package learn.gjx.tuku1.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.ciModel.persistence.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PrivateCosUploadTest {
    @Test
    void originalRequestAndExpectedDerivativesUseExplicitPrivateAcl() {
        COSClient client = mock(COSClient.class);
        CosManager manager = new CosManager();
        ReflectionTestUtils.setField(manager, "cosClient", client);
        String bucket = "private-test-1250000000";
        PicOperations ops = new PicOperations(); ops.setIsPicInfo(1);
        PicOperations.Rule rule = new PicOperations.Rule(); rule.setBucket(bucket);
        rule.setFileId("/space/101/a_display.webp"); rule.setRule("imageMogr2/format/webp");
        ops.setRules(Collections.singletonList(rule));
        CIObject expected = new CIObject(); expected.setKey("space/101/a_display.webp");
        CIObject unexpected = new CIObject(); unexpected.setKey("public/other.jpg");
        ProcessResults processed = new ProcessResults(); processed.setObjectList(Arrays.asList(expected, unexpected));
        CIUploadResult ci = new CIUploadResult(); ci.setProcessResults(processed);
        PutObjectResult result = new PutObjectResult(); result.setCiUploadResult(ci);
        when(client.putObject(any(PutObjectRequest.class))).thenReturn(result);
        manager.putPrivatePictureObject(bucket, "space/101/a.jpg", new File("not-read-by-mock.jpg"), ops);
        ArgumentCaptor<PutObjectRequest> capture = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(capture.capture());
        assertEquals(bucket, capture.getValue().getBucketName());
        assertEquals(CannedAccessControlList.Private, capture.getValue().getCannedAcl());
        assertSame(ops, capture.getValue().getPicOperations());
        verify(client).setObjectAcl(bucket, "space/101/a_display.webp", CannedAccessControlList.Private);
        verify(client, never()).setObjectAcl(eq(bucket), eq("public/other.jpg"), any(CannedAccessControlList.class));
    }
}
