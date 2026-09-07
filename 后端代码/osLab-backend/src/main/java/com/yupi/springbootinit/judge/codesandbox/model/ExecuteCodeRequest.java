package com.yupi.springbootinit.judge.codesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeRequest {
    private List<String> inputList;

    private String code;

    private String language;

    /**
     * Per-test-case execution limit in milliseconds. The remote sandbox clamps
     * this value to its own safe operating range.
     */
    private Long timeLimit;

    /**
     * Maximum memory for a worker process in megabytes.
     */
    private Long memoryLimit;
}
