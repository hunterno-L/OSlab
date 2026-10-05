package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 页面置换模拟器入口参数
 */
@Data
public class SimulationEntryVO implements Serializable {
    private String nodeName;
    private List<String> algorithms;
}
