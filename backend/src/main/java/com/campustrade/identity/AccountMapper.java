package com.campustrade.identity;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
interface AccountMapper {
    @Select("SELECT id,username,display_name,password_hash,role FROM users WHERE username=#{username}")
    Account findByUsername(String username);

    @Select("SELECT id,username,display_name,password_hash,role FROM users WHERE id=#{id}")
    Account findById(long id);

    @Insert("INSERT INTO users(username,display_name,password_hash) VALUES(#{username},#{displayName},#{passwordHash})")
    void insert(String username, String displayName, String passwordHash);
}
