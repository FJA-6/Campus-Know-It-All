package com.fja.ai.tinyrag.auth;

import com.fja.ai.tinyrag.auth.dto.AuthRequest;
import com.fja.ai.tinyrag.auth.dto.AuthResponse;
import com.fja.ai.tinyrag.auth.dto.AuthUserDto;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    public static final String SESSION_USER_ID = "LOGIN_USER_ID";
    public static final String SESSION_USERNAME = "LOGIN_USERNAME";

    private final UserAccountRepository userAccountRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository userAccountRepository,
                       org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse register(AuthRequest request, HttpSession session) {
        String username = normalizeUsername(request.getUsername());
        if (userAccountRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        UserAccount saved = userAccountRepository.save(account);
        saveLoginSession(session, saved);
        return new AuthResponse("注册成功", toUserDto(saved));
    }

    public AuthResponse login(AuthRequest request, HttpSession session) {
        String username = normalizeUsername(request.getUsername());
        UserAccount account = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        saveLoginSession(session, account);
        return new AuthResponse("登录成功", toUserDto(account));
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    public AuthUserDto currentUser(HttpSession session) {
        Object id = session.getAttribute(SESSION_USER_ID);
        Object username = session.getAttribute(SESSION_USERNAME);
        if (!(id instanceof Long) || !(username instanceof String)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        Long userId = (Long) id;
        String name = (String) username;
        return new AuthUserDto(userId, name);
    }

    private void saveLoginSession(HttpSession session, UserAccount account) {
        session.setAttribute(SESSION_USER_ID, account.getId());
        session.setAttribute(SESSION_USERNAME, account.getUsername());
    }

    private AuthUserDto toUserDto(UserAccount account) {
        return new AuthUserDto(account.getId(), account.getUsername());
    }

    private String normalizeUsername(String username) {
        String v = username == null ? "" : username.trim();
        if (v.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        return v;
    }
}
