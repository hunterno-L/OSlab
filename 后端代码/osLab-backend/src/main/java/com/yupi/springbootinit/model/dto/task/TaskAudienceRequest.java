package com.yupi.springbootinit.model.dto.task;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 发布对象选择：班级名和/或具体学生 userId。 */
@Data
public class TaskAudienceRequest implements Serializable {
    private List<String> classNames = new ArrayList<>();
    private List<Long> userIds = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
