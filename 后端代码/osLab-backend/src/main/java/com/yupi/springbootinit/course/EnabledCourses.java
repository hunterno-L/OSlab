package com.yupi.springbootinit.course;

import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EnabledCourses {
    private final Set<String> enabled;
    public EnabledCourses(@Value("${oslab.learning.enabled-courses:course.operating-systems}") String codes) {
        enabled = Arrays.stream(codes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }
    public Set<String> all() { return java.util.Collections.unmodifiableSet(enabled); }
    public boolean contains(String code) { return code != null && enabled.contains(code.trim()); }
    public void require(String code) {
        if (!contains(code)) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "课程当前不可用");
    }
}
