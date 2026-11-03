package com.yupi.springbootinit.ai;

import com.yupi.springbootinit.model.vo.ai.NextAction;
import com.yupi.springbootinit.model.vo.ai.SourceRef;
import com.yupi.springbootinit.model.vo.ai.TargetRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 计划/来源/轨迹都用 JSON 列存储，列表反序列化必须真的还原成元素列表。
 *
 * <p>这里锁住 {@code readList} 的语义：返回值是 {@code List<T>} 本身，
 * 不能是"只含一个 List 元素的列表"——后者会在调用方迭代时抛 ClassCastException，
 * 而且因为被 catch 吞掉，表现为"来源 0 条""计划为空"这种静默数据丢失。
 */
class AiJsonTest {

    @Test
    void sourceRefListRoundTripsAsElements() {
        List<SourceRef> refs = new ArrayList<>();
        refs.add(SourceRef.of("chunk.a", "COURSE", "course.operating-systems", 2,
                "block.a", "2.2.5", "LRU 命中后更新顺序"));
        refs.add(SourceRef.of("material:1", "PERSONAL", "course.operating-systems", 1,
                "material:1:0", "我的笔记", "个人资料片段"));

        List<SourceRef> parsed = AiJson.readList(AiJson.write(refs), SourceRef.class);

        assertEquals(2, parsed.size());
        assertEquals("chunk.a", parsed.get(0).getSourceId());
        assertEquals("COURSE", parsed.get(0).getOwnerScope());
        assertEquals(2, parsed.get(0).getRevision());
        assertEquals("我的笔记", parsed.get(1).getLocator());
    }

    @Test
    void nextActionListRoundTripsIncludingNestedTargetRef() {
        NextAction action = new NextAction();
        action.setActionId("na-31-0");
        action.setType(NextAction.TYPE_PREDICT);
        action.setReason("区分候选原因");
        action.setEstimatedMinutes(3);
        action.setStatus(NextAction.STATUS_PENDING);
        action.setTargetRef(new TargetRef(TargetRef.KIND_SIMULATION, "page.lru-vs-fifo.victim", null));
        action.setEvidenceIds(new ArrayList<>(List.of("1001", "1002")));

        List<NextAction> parsed = AiJson.readList(AiJson.write(List.of(action)), NextAction.class);

        assertEquals(1, parsed.size());
        NextAction read = parsed.get(0);
        assertEquals("na-31-0", read.getActionId());
        assertEquals(NextAction.TYPE_PREDICT, read.getType());
        assertNotNull(read.getTargetRef(), "嵌套对象必须一起还原");
        assertEquals("page.lru-vs-fifo.victim", read.getTargetRef().getCode());
        assertEquals(2, read.getEvidenceIds().size());
        assertTrue(read.isAssessed(), "由 type 推导的派生属性在反序列化后仍成立");
    }

    @Test
    void stringListRoundTrips() {
        List<String> parsed = AiJson.readList(AiJson.write(List.of("e1", "e2")), String.class);
        assertEquals(List.of("e1", "e2"), parsed);
    }

    @Test
    void blankAndBrokenInputDegradeToEmptyListWithoutThrowing() {
        assertTrue(AiJson.readList(null, NextAction.class).isEmpty());
        assertTrue(AiJson.readList("   ", NextAction.class).isEmpty());
        assertTrue(AiJson.readList("not-json", NextAction.class).isEmpty());
        assertTrue(AiJson.readList("{\"a\":1}", NextAction.class).isEmpty());
    }

    @Test
    void idHelpersKeepSnowflakePrecision() {
        Long id = 2117604565077159938L;
        assertEquals("2117604565077159938", AiJson.id(id));
        assertEquals(id, AiJson.parseId("2117604565077159938"));
        assertNull(AiJson.id(null));
    }
}
