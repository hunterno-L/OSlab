package com.yupi.springbootinit.model.dto.task;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 创建/保存任务草稿。 */
@Data
public class TaskSaveRequest implements Serializable {
    private String title;
    private String description;
    private String courseCode;
    private String unitCode;
    /** ISO-8601 本地时间；空表示不设截止时间 */
    private String dueAt;
    private Boolean allowLate;
    private Integer rowVersion;
    private TaskAudienceRequest audience;
    private List<TaskItemRequest> items = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
