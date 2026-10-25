package com.yupi.springbootinit.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.model.entity.User;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import static com.yupi.springbootinit.course.CourseJson.*;

@RestController
@RequestMapping("/courses")
public class CourseController {
    @Resource CourseService courses;
    @Resource CourseRepository db;
    @Resource UserService users;
    @Resource com.yupi.springbootinit.learning.AssessmentAssetService assessmentAssets;
    long user(HttpServletRequest request){return users.getLoginUser(request).getId();}
    @GetMapping public BaseResponse<?> list(HttpServletRequest r){return ResultUtils.success(courses.courses(user(r)));}
    @GetMapping("/{courseCode}/outline") public BaseResponse<?> outline(@PathVariable String courseCode,HttpServletRequest r){return ResultUtils.success(courses.outline(courseCode,user(r),false));}
    @GetMapping("/{courseCode}/resume") public BaseResponse<?> resume(@PathVariable String courseCode,HttpServletRequest r){return ResultUtils.success(courses.resume(courseCode,user(r)));}
    @GetMapping("/units/{unitCode}") public BaseResponse<?> unit(@PathVariable String unitCode,HttpServletRequest r){return ResultUtils.success(courses.unit(unitCode,user(r)));}
    @PostMapping("/units/{unitCode}/position") public BaseResponse<?> position(@PathVariable String unitCode,@RequestBody JsonNode body,HttpServletRequest r){return ResultUtils.success(courses.selectActivity(unitCode,body,user(r)));}
    @PostMapping("/activities/{activityCode}/complete") public BaseResponse<?> complete(@PathVariable String activityCode,@RequestBody JsonNode body,HttpServletRequest r){return ResultUtils.success(courses.complete(activityCode,body,user(r)));}
    @GetMapping("/nodes/{nodeCode}/units") public BaseResponse<?> node(@PathVariable String nodeCode,HttpServletRequest r){return ResultUtils.success(courses.nodeUnits(nodeCode,user(r)));}
    @GetMapping("/assets/hash/{hash}") public ResponseEntity<byte[]> assessmentAsset(@PathVariable String hash,HttpServletRequest r) {
        User viewer=users.getLoginUser(r);Map<String,Object> asset=assessmentAssets.byHash(hash);
        if(!"admin".equals(viewer.getUserRole())&&!assessmentAssets.studentCanRead("/api/courses/assets/hash/"+hash,viewer.getId()))throw bad("图片尚未公开或当前不可查看");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(db.value(asset,"contentType"))).header("X-Content-Type-Options","nosniff").header("Cache-Control","private, no-store").body((byte[])asset.get("contentBytes"));
    }
    @GetMapping("/assets/{assetId}") public ResponseEntity<byte[]> asset(@PathVariable long assetId,HttpServletRequest r) {
        User viewer=users.getLoginUser(r);Map<String,Object> asset=db.one("SELECT * FROM course_asset WHERE id=?",assetId);if(asset==null)throw bad("图片不存在");
        boolean allowed="admin".equals(viewer.getUserRole());
        if(!allowed)for(Map<String,Object> row:db.rows("SELECT publishedJson FROM learning_content_chunk WHERE publishedJson IS NOT NULL AND isDelete=0")) {
            JsonNode chunk=parse(db.value(row,"publishedJson"));
            for(JsonNode a:chunk.path("assets"))if(db.value(asset,"assetPath").equals(text(chunk,"assetBatchCode")+"/"+text(a,"path")))allowed=true;
        }
        if(!allowed)throw bad("图片尚未发布");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(db.value(asset,"contentType"))).header("X-Content-Type-Options","nosniff").header("Cache-Control","private, max-age=3600").body((byte[])asset.get("contentBytes"));
    }
}
