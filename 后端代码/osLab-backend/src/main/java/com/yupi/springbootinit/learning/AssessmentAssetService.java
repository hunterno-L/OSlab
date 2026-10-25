package com.yupi.springbootinit.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yupi.springbootinit.course.CourseContentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import static com.yupi.springbootinit.learning.TeachingAssessmentService.*;

/** Content-addressed images keep identical reimports stable; source/answer scans stay administrator-only. */
@Service
public class AssessmentAssetService {
    @Resource private CourseContentService content;
    @Resource private JdbcTemplate jdbc;

    public JsonNode prepare(JsonNode question,JsonNode request) {
        JsonNode assets=request.path("assets");content.validateAssets(assets);
        Map<String,String> urls=new LinkedHashMap<>();
        for(JsonNode asset:assets) {
            byte[] bytes=Base64.getDecoder().decode(required(asset,"contentBase64"));
            urls.put(required(asset,"path"),"/api/courses/assets/hash/"+hex(digest(bytes)));
        }
        JsonNode transformed=rewrite(question,urls);
        if(Pattern.compile("assets/[^\\s)\"<>]+\\.(png|jpe?g|webp|gif)",Pattern.CASE_INSENSITIVE).matcher(transformed.toString()).find())
            throw bad("题目引用的图片未随包上传，请同时选择对应 assets 文件");
        return transformed;
    }
    public void archive(JsonNode pack,JsonNode request) {content.storeAssets(pack,request.path("assets"));}
    JsonNode rewrite(JsonNode input,Map<String,String> urls) {
        if(input.isTextual()) {String value=input.asText();for(Map.Entry<String,String> entry:urls.entrySet())value=value.replace(entry.getKey(),entry.getValue());return TextNode.valueOf(value);}
        if(input.isObject()) {ObjectNode out=((ObjectNode)input).deepCopy();input.fields().forEachRemaining(e->out.set(e.getKey(),rewrite(e.getValue(),urls)));return out;}
        if(input.isArray()) {ArrayNode out=((ArrayNode)input).arrayNode();for(JsonNode item:input)out.add(rewrite(item,urls));return out;}
        return input.deepCopy();
    }
    public Map<String,Object> byHash(String hash) {
        if(!hash.matches("[a-f0-9]{64}"))throw bad("图片不存在");
        byte[] bytes=new byte[32];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(hash.substring(i*2,i*2+2),16);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM course_asset WHERE contentHash=? ORDER BY id LIMIT 1",Base64.getEncoder().encodeToString(bytes));
        if(rows.isEmpty())throw bad("图片不存在");return rows.get(0);
    }
    public boolean studentCanRead(String url,long userId) {
        String candidate="%"+url+"%";
        for(Map<String,Object> q:jdbc.queryForList("SELECT id,content,options,analysis FROM learning_question WHERE reviewStatus='PUBLISHED' AND resourceScope='SHARED' AND isDelete=0 AND (content LIKE ? OR options LIKE ? OR analysis LIKE ?)",candidate,candidate,candidate)) {
            if(references(q.get("content"),url)||references(q.get("options"),url))return true;
            if(references(q.get("analysis"),url)&&jdbc.queryForObject("SELECT COUNT(*) FROM learning_attempt WHERE questionId=? AND userId=? AND hasViewedAnalysis=1",Long.class,q.get("id"),userId)>0)return true;
        }
        for(String snapshot:jdbc.queryForList("SELECT questionSnapshot FROM learning_attempt WHERE userId=? AND hasViewedAnalysis=1 AND questionSnapshot IS NOT NULL AND questionSnapshot LIKE ?",String.class,userId,candidate)) {
            JsonNode q=parse(snapshot);if(references(q.path("content").asText(),url)||references(q.path("options").asText(),url)||references(q.path("analysis").asText(),url))return true;
        }
        return false;
    }
    boolean references(Object text,String url) {return text!=null&&Pattern.compile(Pattern.quote(url)+"(?![a-f0-9])").matcher(text.toString()).find();}
    static byte[] digest(byte[] bytes) {try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(Exception e){throw new IllegalStateException(e);}}
    static String hex(byte[] bytes) {StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}
}
