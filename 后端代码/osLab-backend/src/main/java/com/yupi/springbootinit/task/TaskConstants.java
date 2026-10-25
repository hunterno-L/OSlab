package com.yupi.springbootinit.task;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 学习任务固定契约值。 */
public final class TaskConstants {
    public static final String TASK_DRAFT = "DRAFT";
    public static final String TASK_PUBLISHED = "PUBLISHED";
    public static final String TASK_CLOSED = "CLOSED";

    public static final String R_PENDING = "PENDING";
    public static final String R_IN_PROGRESS = "IN_PROGRESS";
    public static final String R_SUBMITTED = "SUBMITTED";
    public static final String R_RETURNED = "RETURNED";
    public static final String R_REVIEWED = "REVIEWED";

    public static final String ITEM_TEXT = "TEXT";
    public static final String ITEM_MATERIAL = "MATERIAL";
    public static final String ITEM_QUESTION = "QUESTION";
    public static final String ITEM_EXPERIMENT = "EXPERIMENT";
    public static final Set<String> ITEM_TYPES = new HashSet<>(Arrays.asList(
            ITEM_TEXT, ITEM_MATERIAL, ITEM_QUESTION, ITEM_EXPERIMENT));

    public static final String MODE_REFERENCE = "REFERENCE";
    public static final String MODE_AUTHORED = "AUTHORED";

    public static final String SCOPE_SHARED = "SHARED";
    public static final String SCOPE_TASK = "TASK";

    public static final String PURPOSE_TEACHER_MATERIAL = "TEACHER_MATERIAL";
    public static final String PURPOSE_STUDENT_WORK = "STUDENT_WORK";

    public static final Set<String> ALLOWED_EXT = new HashSet<>(Arrays.asList(
            "doc", "docx", "pdf", "txt", "md", "png", "jpg", "jpeg", "webp"));

    public static final Set<String> LANGUAGES = new HashSet<>(Arrays.asList("java", "cpp", "c", "go"));

    private TaskConstants() {
    }
}
