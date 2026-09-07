package com.yupi.springbootinit.service.impl;

import static com.yupi.springbootinit.constant.UserConstant.USER_LOGIN_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yupi.springbootinit.mapper.UserMapper;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.LoginUserVO;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.DigestUtils;

/**
 * 登录兼容性回归测试。
 */
class UserServiceImplTest {

    @Test
    void userLoginAcceptsShortLegacyPasswordAndMigratesIt() {
        String legacyPassword = "legacy";
        User user = new User();
        user.setId(1L);
        user.setUserAccount("legacy-user");
        user.setUserPassword(DigestUtils.md5DigestAsHex(
                (UserServiceImpl.SALT + legacyPassword).getBytes(StandardCharsets.UTF_8)));

        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        HttpSession session = mock(HttpSession.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession()).thenReturn(session);

        UserServiceImpl userService = new UserServiceImpl();
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);

        LoginUserVO loginUser = userService.userLogin(user.getUserAccount(), legacyPassword, request);

        assertNotNull(loginUser);
        assertEquals(user.getId(), loginUser.getId());
        assertTrue(user.getUserPassword().startsWith("$2"));
        verify(userMapper).updateById(any(User.class));
        verify(session).setAttribute(eq(USER_LOGIN_STATE), same(user));
    }
}
