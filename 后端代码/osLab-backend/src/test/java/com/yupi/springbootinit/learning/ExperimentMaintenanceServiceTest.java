package com.yupi.springbootinit.learning;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.springbootinit.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
class ExperimentMaintenanceServiceTest {
 @Test void attachmentsRequireExactSafeUtf8PathAndRealJudgeCase()throws Exception{ExperimentMaintenanceService s=new ExperimentMaintenanceService();ObjectMapper j=new ObjectMapper();String content="[{\"input\":\"\",\"output\":\"ok\"}]";String encoded=Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));assertEquals(content,s.files(j.readTree("{\"files\":[{\"path\":\"experiment-files/42/cases.json\",\"contentBase64\":\""+encoded+"\"}]}" )).get("experiment-files/42/cases.json"));assertThrows(BusinessException.class,()->s.files(j.readTree("{\"files\":[{\"path\":\"experiment-files/../secret\",\"contentBase64\":\"YQ==\"}]}")));assertThrows(BusinessException.class,()->s.validateContent("judgeCase","[]"));assertThrows(BusinessException.class,()->s.validateContent("judgeCase","[{\"input\":1,\"output\":true}]"));assertDoesNotThrow(()->s.validateContent("judgeCase",content));}
}
