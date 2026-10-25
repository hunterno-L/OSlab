package com.yupi.springbootinit.learning;
import com.yupi.springbootinit.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ActivityContextServiceTest {
 @Test void independentEntryWorksAndPartialOrWrongContextFails(){JdbcTemplate db=mock(JdbcTemplate.class);ActivityContextService s=new ActivityContextService();ReflectionTestUtils.setField(s,"jdbc",db);s.validate(null,null,42,false);verifyNoInteractions(db);assertThrows(BusinessException.class,()->s.validate("unit.test",null,42,false));when(db.queryForList(anyString(),eq(String.class),any())).thenReturn(List.of("{\"activities\":[{\"activityCode\":\"act.test\",\"activityType\":\"PRACTICE\",\"reference\":{\"kind\":\"QUESTION\",\"code\":\"q.test\"}}]}"));when(db.queryForObject(anyString(),eq(Integer.class),any(),any())).thenReturn(0);assertThrows(BusinessException.class,()->s.validate("unit.test","act.test",42,false));when(db.queryForObject(anyString(),eq(Integer.class),any(),any())).thenReturn(1);assertDoesNotThrow(()->s.validate("unit.test","act.test",42,false));}
}
