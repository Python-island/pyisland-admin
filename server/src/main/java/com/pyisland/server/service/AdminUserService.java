package com.pyisland.server.service;

import com.pyisland.server.entity.AdminUser;
import com.pyisland.server.repository.AdminUserMapper;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class AdminUserService {

    private final AdminUserMapper adminUserMapper;

    public AdminUserService(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    public AdminUser authenticate(String username, String password) {
        AdminUser user = adminUserMapper.selectByUsername(username);
        if (user != null && user.getPassword().equals(hashPassword(password))) {
            return user;
        }
        return null;
    }

    public AdminUser register(String username, String password) {
        AdminUser existing = adminUserMapper.selectByUsername(username);
        if (existing != null) {
            return null;
        }
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setPassword(hashPassword(password));
        user.setCreatedAt(LocalDateTime.now());
        adminUserMapper.insert(user);
        return user;
    }

    public java.util.List<AdminUser> listAll() {
        return adminUserMapper.selectAll();
    }

    public boolean deleteUser(String username) {
        return adminUserMapper.deleteByUsername(username) > 0;
    }

    public int count() {
        return adminUserMapper.count();
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
