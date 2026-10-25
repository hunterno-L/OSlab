package com.yupi.springbootinit.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.mapper.LearningTaskFileMapper;
import com.yupi.springbootinit.mapper.LearningTaskItemMapper;
import com.yupi.springbootinit.mapper.LearningTaskMapper;
import com.yupi.springbootinit.mapper.LearningTaskSubmissionMapper;
import com.yupi.springbootinit.model.entity.LearningTask;
import com.yupi.springbootinit.model.entity.LearningTaskFile;
import com.yupi.springbootinit.model.entity.LearningTaskItem;
import com.yupi.springbootinit.model.entity.LearningTaskRecipient;
import com.yupi.springbootinit.model.entity.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 任务私有附件：服务器私有目录、服务端存储键、按任务身份授权。 */
@Service
public class TaskFileService {
    @Resource
    private LearningTaskFileMapper fileMapper;
    @Resource
    private LearningTaskMapper taskMapper;
    @Resource
    private LearningTaskItemMapper itemMapper;
    @Resource
    private LearningTaskSubmissionMapper submissionMapper;
    @Resource
    private TaskAccessService access;
    @Resource
    private TaskAudienceService audience;

    @Value("${oslab.tasks.storage-dir:./data/task-files}")
    private String storageDir;
    @Value("${oslab.tasks.max-file-bytes:10485760}")
    private long maxFileBytes;
    @Value("${oslab.tasks.max-files-per-item:5}")
    private int maxFilesPerItem;

    public Map<String, Object> save(MultipartFile multipartFile, String taskCode, String itemCode,
                                    String purpose, User user) {
        ThrowUtils.throwIf(multipartFile == null || multipartFile.isEmpty(),
                ErrorCode.PARAMS_ERROR, "文件不能为空");
        ThrowUtils.throwIf(StringUtils.isAnyBlank(taskCode, itemCode, purpose),
                ErrorCode.PARAMS_ERROR);
        LearningTask task = access.task(taskCode);
        LearningTaskItem item = requireItem(task.getId(), itemCode);
        String normalizedPurpose = purpose.trim().toUpperCase(Locale.ROOT);
        Long recipientId = null;
        if (TaskConstants.PURPOSE_TEACHER_MATERIAL.equals(normalizedPurpose)) {
            ThrowUtils.throwIf(!task.getCreatorId().equals(user.getId()),
                    ErrorCode.NO_AUTH_ERROR, "仅任务创建者可上传资料");
            ThrowUtils.throwIf(!TaskConstants.TASK_DRAFT.equals(task.getStatus()),
                    ErrorCode.OPERATION_ERROR, "任务发布后不能修改资料文件");
        } else if (TaskConstants.PURPOSE_STUDENT_WORK.equals(normalizedPurpose)) {
            ThrowUtils.throwIf(!TaskConstants.TASK_PUBLISHED.equals(task.getStatus()),
                    ErrorCode.OPERATION_ERROR, "任务当前不可上传文件");
            LearningTaskRecipient recipient = access.requireAccepted(task, user);
            access.requireEditable(recipient, access.editableRound(recipient.getId()));
            recipientId = recipient.getId();
        } else {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件用途无效");
        }
        ThrowUtils.throwIf(multipartFile.getSize() > maxFileBytes,
                ErrorCode.PARAMS_ERROR, "文件不能超过 10MB");
        String originalName = safeOriginalName(multipartFile.getOriginalFilename());
        String ext = extension(originalName);
        ThrowUtils.throwIf(!TaskConstants.ALLOWED_EXT.contains(ext),
                ErrorCode.PARAMS_ERROR, "不支持的文件类型");
        long count = fileMapper.selectCount(new LambdaQueryWrapper<LearningTaskFile>()
                .eq(LearningTaskFile::getTaskId, task.getId())
                .eq(LearningTaskFile::getItemCode, itemCode)
                .eq(LearningTaskFile::getPurpose, normalizedPurpose)
                .eq(LearningTaskFile::getIsDelete, 0)
                .eq(recipientId != null, LearningTaskFile::getRecipientId, recipientId));
        ThrowUtils.throwIf(count >= maxFilesPerItem, ErrorCode.PARAMS_ERROR,
                "每个内容块最多上传 " + maxFilesPerItem + " 个附件");

        String storageKey = safeSegment(taskCode) + "/" + safeSegment(itemCode) + "/"
                + IdWorker.getId() + "." + ext;
        Path root = root();
        Path target = root.resolve(storageKey).normalize();
        ThrowUtils.throwIf(!target.startsWith(root), ErrorCode.PARAMS_ERROR, "文件路径无效");
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(multipartFile.getInputStream(), digest)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            LearningTaskFile record = new LearningTaskFile();
            record.setId(IdWorker.getId());
            record.setTaskId(task.getId());
            record.setItemCode(itemCode);
            record.setRecipientId(recipientId);
            record.setOwnerId(user.getId());
            record.setPurpose(normalizedPurpose);
            record.setOriginalName(originalName);
            record.setStorageKey(storageKey);
            record.setExt(ext);
            record.setMime(multipartFile.getContentType());
            record.setSize(multipartFile.getSize());
            record.setSha256(hex(digest.digest()));
            record.setIsDelete(0);
            fileMapper.insert(record);
            return metadata(record);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败");
        }
    }

    public Download download(long fileId, User user) {
        LearningTaskFile file = fileMapper.selectById(fileId);
        ThrowUtils.throwIf(file == null || Integer.valueOf(1).equals(file.getIsDelete()),
                ErrorCode.NOT_FOUND_ERROR, "文件不存在");
        LearningTask task = taskMapper.selectById(file.getTaskId());
        ThrowUtils.throwIf(task == null || Integer.valueOf(1).equals(task.getIsDelete()),
                ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        boolean creator = task.getCreatorId().equals(user.getId());
        boolean recipient = access.recipient(task.getId(), user.getId()) != null;
        if (TaskConstants.PURPOSE_TEACHER_MATERIAL.equals(file.getPurpose())) {
            ThrowUtils.throwIf(!creator && !recipient, ErrorCode.NO_AUTH_ERROR, "无权下载该文件");
        } else {
            ThrowUtils.throwIf(!creator && !file.getOwnerId().equals(user.getId()),
                    ErrorCode.NO_AUTH_ERROR, "无权下载该文件");
        }
        Path root = root();
        Path path = root.resolve(file.getStorageKey()).normalize();
        ThrowUtils.throwIf(!path.startsWith(root) || !Files.isRegularFile(path),
                ErrorCode.NOT_FOUND_ERROR, "文件不存在");
        return new Download(new FileSystemResource(path), file.getOriginalName(),
                mediaType(file.getExt()), file.getSize());
    }

    public void delete(long fileId, User user) {
        LearningTaskFile file = fileMapper.selectById(fileId);
        ThrowUtils.throwIf(file == null || Integer.valueOf(1).equals(file.getIsDelete()),
                ErrorCode.NOT_FOUND_ERROR, "文件不存在");
        LearningTask task = taskMapper.selectById(file.getTaskId());
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        if (TaskConstants.PURPOSE_TEACHER_MATERIAL.equals(file.getPurpose())) {
            ThrowUtils.throwIf(!task.getCreatorId().equals(user.getId()),
                    ErrorCode.NO_AUTH_ERROR, "仅任务创建者可删除资料");
            ThrowUtils.throwIf(!TaskConstants.TASK_DRAFT.equals(task.getStatus()),
                    ErrorCode.OPERATION_ERROR, "任务发布后不能删除资料文件");
        } else {
            ThrowUtils.throwIf(!file.getOwnerId().equals(user.getId()),
                    ErrorCode.NO_AUTH_ERROR, "只能删除本人上传的文件");
        }
        ThrowUtils.throwIf(referenced(file.getId()), ErrorCode.OPERATION_ERROR,
                "文件已被正式提交引用，不能删除");
        file.setIsDelete(1);
        fileMapper.updateById(file);
        try {
            Files.deleteIfExists(root().resolve(file.getStorageKey()).normalize());
        } catch (IOException ignored) {
            // 元数据已标记删除，物理文件保留不影响授权
        }
    }

    public List<LearningTaskFile> activeFiles(Long taskId, String itemCode, String purpose, Long recipientId) {
        return fileMapper.selectList(new LambdaQueryWrapper<LearningTaskFile>()
                .eq(LearningTaskFile::getTaskId, taskId)
                .eq(LearningTaskFile::getItemCode, itemCode)
                .eq(LearningTaskFile::getPurpose, purpose)
                .eq(LearningTaskFile::getIsDelete, 0)
                .eq(recipientId != null, LearningTaskFile::getRecipientId, recipientId));
    }

    public void validateTeacherFiles(Long taskId, String itemCode, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return;
        Set<Long> unique = new LinkedHashSet<>(fileIds);
        for (Long fileId : unique) {
            LearningTaskFile file = fileMapper.selectById(fileId);
            ThrowUtils.throwIf(file == null || Integer.valueOf(1).equals(file.getIsDelete())
                            || !TaskConstants.PURPOSE_TEACHER_MATERIAL.equals(file.getPurpose())
                            || !taskId.equals(file.getTaskId()) || !itemCode.equals(file.getItemCode()),
                    ErrorCode.PARAMS_ERROR, "资料文件不属于当前任务内容块");
        }
    }

    public void validateStudentFiles(Long taskId, String itemCode, Long recipientId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return;
        for (Long fileId : new LinkedHashSet<>(fileIds)) {
            LearningTaskFile file = fileMapper.selectById(fileId);
            ThrowUtils.throwIf(file == null || Integer.valueOf(1).equals(file.getIsDelete())
                            || !TaskConstants.PURPOSE_STUDENT_WORK.equals(file.getPurpose())
                            || !taskId.equals(file.getTaskId()) || !itemCode.equals(file.getItemCode())
                            || !recipientId.equals(file.getRecipientId()),
                    ErrorCode.PARAMS_ERROR, "提交文件不属于本人或当前内容块");
        }
    }

    public List<Map<String, Object>> metadataList(List<Long> fileIds) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (fileIds == null) return out;
        for (Long id : new LinkedHashSet<>(fileIds)) {
            LearningTaskFile file = fileMapper.selectById(id);
            if (file != null && !Integer.valueOf(1).equals(file.getIsDelete())) out.add(metadata(file));
        }
        return out;
    }

    public Map<String, Object> metadata(LearningTaskFile file) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileId", String.valueOf(file.getId()));
        out.put("originalName", file.getOriginalName());
        out.put("ext", file.getExt());
        out.put("size", file.getSize());
        out.put("purpose", file.getPurpose());
        out.put("downloadPath", "/api/task-files/" + file.getId());
        return out;
    }

    private boolean referenced(Long fileId) {
        String needle = "\"" + fileId + "\"";
        Long count = submissionMapper.selectCount(new LambdaQueryWrapper<com.yupi.springbootinit.model.entity.LearningTaskSubmission>()
                .like(com.yupi.springbootinit.model.entity.LearningTaskSubmission::getSubmittedSnapshotJson, needle));
        return count != null && count > 0;
    }

    private LearningTaskItem requireItem(Long taskId, String itemCode) {
        LearningTaskItem item = itemMapper.selectOne(new LambdaQueryWrapper<LearningTaskItem>()
                .eq(LearningTaskItem::getTaskId, taskId)
                .eq(LearningTaskItem::getItemCode, itemCode)
                .last("limit 1"));
        ThrowUtils.throwIf(item == null, ErrorCode.NOT_FOUND_ERROR, "任务内容块不存在");
        return item;
    }

    private Path root() {
        Path root = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "附件目录不可用");
        }
        return root;
    }

    private static String safeOriginalName(String name) {
        String value = StringUtils.defaultIfBlank(name, "attachment");
        value = value.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        if (value.length() > 200) value = value.substring(value.length() - 200);
        return value.trim().isEmpty() ? "attachment" : value;
    }

    private static String safeSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }

    private static MediaType mediaType(String ext) {
        switch (ext) {
            case "png": return MediaType.IMAGE_PNG;
            case "jpg":
            case "jpeg": return MediaType.IMAGE_JPEG;
            case "webp": return MediaType.parseMediaType("image/webp");
            case "pdf": return MediaType.APPLICATION_PDF;
            case "txt":
            case "md": return MediaType.TEXT_PLAIN;
            default: return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public static class Download {
        public final org.springframework.core.io.Resource resource;
        public final String originalName;
        public final MediaType mediaType;
        public final long size;

        public Download(org.springframework.core.io.Resource resource, String originalName,
                        MediaType mediaType, long size) {
            this.resource = resource;
            this.originalName = originalName;
            this.mediaType = mediaType;
            this.size = size;
        }
    }
}
