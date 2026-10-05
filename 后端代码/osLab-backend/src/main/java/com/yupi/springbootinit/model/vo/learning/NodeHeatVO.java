package com.yupi.springbootinit.model.vo.learning;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 班级节点热力 VO
 */
@Data
public class NodeHeatVO implements Serializable {

    /**
     * 知识节点业务键
     */
    private String nodeName;

    /**
     * 有证据的学生数
     */
    private Integer studentCount;

    /**
     * 班级平均 pKnown（Jackson 显式固定键名，原因同 MasteryNodeVO.pKnown）
     */
    @JsonProperty("avgPKnown")
    private Double avgPKnown;

    /**
     * 薄弱（RED）学生数
     */
    private Integer weakCount;
}
