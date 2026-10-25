package com.yupi.springbootinit.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.yupi.springbootinit.course.CourseContentService;
import com.yupi.springbootinit.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssessmentAssetServiceTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void imageUrlsAreContentAddressedAndMissingFilesAreReported() throws Exception {
        AssessmentAssetService service=new AssessmentAssetService();
        ReflectionTestUtils.setField(service,"content",new CourseContentService());
        String bytes="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aOJ8AAAAASUVORK5CYII=";
        JsonNode request=json.readTree("{\"assets\":[{\"path\":\"assets/figure.png\",\"contentType\":\"image/png\",\"contentBase64\":\""+bytes+"\"}]}");
        JsonNode q=json.readTree("{\"content\":\"![图](assets/figure.png)\",\"sourceRef\":{\"sourceAsset\":\"assets/figure.png\"}}");
        JsonNode first=service.prepare(q,request),second=service.prepare(q,request);
        assertEquals(first,second);assertTrue(first.path("content").asText().contains("/api/courses/assets/hash/"));
        assertThrows(BusinessException.class,()->service.prepare(q,json.createObjectNode()));
    }
    @Test void answerAndProvenanceImagesAreNotPublicBeforeAnAttempt() {
        AssessmentAssetService service=new AssessmentAssetService();JdbcTemplate db=mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service,"jdbc",db);String url="/api/courses/assets/hash/"+"a".repeat(64);
        Map<String,Object> row=new HashMap<>();row.put("id",42L);row.put("content","Question");row.put("options","[]");row.put("analysis","![answer]("+url+")");
        when(db.queryForList(contains("FROM learning_question"),anyString(),anyString(),anyString())).thenReturn(Collections.singletonList(row));
        when(db.queryForObject(contains("learning_attempt"),eq(Long.class),any(Object.class),any(Object.class))).thenReturn(0L);
        assertFalse(service.studentCanRead(url,7));
        when(db.queryForObject(contains("learning_attempt"),eq(Long.class),any(Object.class),any(Object.class))).thenReturn(1L);
        assertTrue(service.studentCanRead(url,7));
        row.put("analysis","No image");assertFalse(service.studentCanRead(url,7));
        row.put("content","![question]("+url+")");assertTrue(service.studentCanRead(url,7));
    }
}
