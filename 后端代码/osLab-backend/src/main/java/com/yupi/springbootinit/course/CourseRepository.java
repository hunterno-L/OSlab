package com.yupi.springbootinit.course;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import javax.annotation.Resource;

/** SQL uses project camelCase columns; all table names below are fixed server constants. */
@Repository
public class CourseRepository {
    @Resource JdbcTemplate jdbc;
    public List<Map<String,Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    public Map<String,Object> one(String sql, Object... args) {
        List<Map<String,Object>> rows = rows(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
    public long id() { return IdWorker.getId(); }
    public Map<String,Object> entity(String table, String column, String code) {
        return one("SELECT * FROM " + table + " WHERE " + column + "=? AND isDelete=0", code);
    }
    public boolean exists(String table, String column, String code) { return entity(table,column,code) != null; }
    public String value(Map<String,Object> row, String key) { return row == null || row.get(key)==null ? "" : row.get(key).toString(); }
}
