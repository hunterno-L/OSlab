package com.yupi.springbootinit.service;

import com.yupi.springbootinit.model.dto.learning.SimulationCpuRequest;
import com.yupi.springbootinit.model.dto.learning.SimulationPageRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.SimulationCpuVO;
import com.yupi.springbootinit.model.vo.learning.SimulationPageVO;

/**
 * 模拟器服务（WP6）：确定性算法计算 + 预测练习服务端判分 + 学习事件接入
 */
public interface LearningSimulationService {

    /**
     * CPU 调度模拟（FCFS/SJF/RR）；predictIndex >= 0 时判分预测并写学习事件
     */
    SimulationCpuVO runCpu(SimulationCpuRequest request, User user);

    /**
     * 页面置换模拟（FIFO/LRU）；predictStep >= 0 时判分预测并写学习事件
     */
    SimulationPageVO runPage(SimulationPageRequest request, User user);
}
