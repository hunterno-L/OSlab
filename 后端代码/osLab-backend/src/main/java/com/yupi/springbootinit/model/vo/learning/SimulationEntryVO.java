package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 节点工作台的算法演示入口；algorithms 为 18 项播放器的稳定算法键。
 */
@Data
public class SimulationEntryVO implements Serializable {
    private String nodeName;
    private List<String> algorithms;
}
