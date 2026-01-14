package com.lumina.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumina.entity.User;
import com.lumina.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
public class UserDetailsServiceImpl implements ReactiveUserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        // 由于 MyBatis 是阻塞的，我们将其放在 boundedElastic 线程池中执行
        return Mono.fromCallable(() -> {
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("username", username);
            User user = userMapper.selectOne(wrapper);

            if (user == null) {
                throw new UsernameNotFoundException("用户不存在: " + username);
            }
            return user;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .map(user -> org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles("USER")
                .build());
    }
}
