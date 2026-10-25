package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.model.vo.learning.SimulationEntryVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningResourceResolverTest {

    @Test
    void stableNodeCodeMapsToLeavesPlayerAlgorithmKey() {
        NodeIdentityService identities = mock(NodeIdentityService.class);
        when(identities.resolve("LRU 页面置换"))
                .thenReturn(new NodeIdentityService.Identity("os.memory.lru", "LRU 页面置换", "LRU 页面置换"));
        LearningResourceResolver resolver = new LearningResourceResolver();
        ReflectionTestUtils.setField(resolver, "nodeIdentities", identities);

        SimulationEntryVO entry = resolver.simulationEntryOf("LRU 页面置换");

        assertEquals(List.of("page.lru"), entry.getAlgorithms());
    }

    @Test
    void legacyNameKeywordFallbackStillOffersStableKeys() {
        NodeIdentityService identities = mock(NodeIdentityService.class);
        when(identities.resolve("页面置换")).thenReturn(null);
        LearningResourceResolver resolver = new LearningResourceResolver();
        ReflectionTestUtils.setField(resolver, "nodeIdentities", identities);

        SimulationEntryVO entry = resolver.simulationEntryOf("页面置换");

        assertEquals(List.of("page.fifo", "page.lru", "page.opt"), entry.getAlgorithms());
    }

    @Test
    void unrelatedNodeHasNoSimulationEntry() {
        NodeIdentityService identities = mock(NodeIdentityService.class);
        when(identities.resolve("进程同步")).thenReturn(null);
        LearningResourceResolver resolver = new LearningResourceResolver();
        ReflectionTestUtils.setField(resolver, "nodeIdentities", identities);

        assertNull(resolver.simulationEntryOf("进程同步"));
    }
}
