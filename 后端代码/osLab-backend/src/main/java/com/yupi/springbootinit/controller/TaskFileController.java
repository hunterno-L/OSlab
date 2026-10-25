package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.task.TaskFileService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 任务私有附件：上传、按身份授权下载、删除。 */
@RestController
@RequestMapping("/task-files")
public class TaskFileController {

    @Resource
    private TaskFileService taskFileService;
    @Resource
    private UserService userService;

    @PostMapping
    public BaseResponse<Map<String, Object>> upload(@RequestPart("file") MultipartFile file,
                                                    @RequestParam String taskCode,
                                                    @RequestParam String itemCode,
                                                    @RequestParam String purpose,
                                                    HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(taskFileService.save(file, taskCode, itemCode, purpose, user));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable long fileId,
                                                                        HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        TaskFileService.Download download = taskFileService.download(fileId, user);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.originalName, StandardCharsets.UTF_8)
                .build();
        MediaType mediaType = download.mediaType == null ? MediaType.APPLICATION_OCTET_STREAM : download.mediaType;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.size)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(download.resource);
    }

    @DeleteMapping("/{fileId}")
    public BaseResponse<Boolean> delete(@PathVariable long fileId, HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        taskFileService.delete(fileId, user);
        return ResultUtils.success(true);
    }
}
