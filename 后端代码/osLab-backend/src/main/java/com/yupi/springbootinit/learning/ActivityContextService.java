package com.yupi.springbootinit.learning;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.*;
import static com.yupi.springbootinit.learning.TeachingAssessmentService.*;

/** Submission context is a server-validated reference, never a client claim of success. */
@Service
public class ActivityContextService {
    @Resource private JdbcTemplate jdbc;
    public void validate(String unit,String activity,long questionId,boolean experiment){
        if(unit==null&&activity==null)return;
        if(unit==null||activity==null||unit.trim().isEmpty()||activity.trim().isEmpty())throw bad("sourceUnitCode/sourceActivityCode 必须一起填写");
        List<String> published=jdbc.queryForList("SELECT publishedJson FROM learning_unit WHERE unitCode=? AND publishedJson IS NOT NULL AND isDelete=0",String.class,unit);
        if(published.size()!=1)throw bad("来源单元尚未发布");
        JsonNode match=null;for(JsonNode a:parse(published.get(0)).path("activities"))if(activity.equals(a.path("activityCode").asText())){match=a;break;}
        if(match==null)throw bad("来源活动不属于单元");String type=match.path("activityType").asText(),code=match.path("reference").path("code").asText();
        if(experiment){if(!"EXPERIMENT".equals(type)||!"RESOURCE".equals(match.path("reference").path("kind").asText())||jdbc.queryForObject("SELECT COUNT(*) FROM course_resource WHERE resourceCode=? AND questionId=? AND resourceType='EXPERIMENT' AND status='PUBLISHED' AND isDelete=0",Integer.class,code,questionId)!=1)throw bad("来源活动实验资源不匹配");}
        else if(!Arrays.asList("PRACTICE","ASSESSMENT").contains(type)||!"QUESTION".equals(match.path("reference").path("kind").asText())||jdbc.queryForObject("SELECT COUNT(*) FROM learning_question WHERE questionCode=? AND id=? AND reviewStatus='PUBLISHED' AND isDelete=0",Integer.class,code,questionId)!=1)throw bad("来源活动题目不匹配");
    }
}
