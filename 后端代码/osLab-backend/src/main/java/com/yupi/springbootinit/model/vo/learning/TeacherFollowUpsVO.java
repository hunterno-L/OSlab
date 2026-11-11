package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;
import java.util.List;

/** Read-only, per-class counts; the three categories can overlap. */
@Data
public class TeacherFollowUpsVO {
    private String className;
    private String courseCode;
    private int studentCount;
    private int dueReviewStudentCount;
    private int weakStudentCount;
    private int missingEvidenceStudentCount;
    private List<Item> items;

    @Data
    public static class Item {
        private String kind;
        private String nodeCode;
        private String nodeName;
        private int studentCount;
    }
}
