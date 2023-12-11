package com.dayone.service;

import com.dayone.exception.impl.AlreadyExistUserException;
import com.dayone.exception.impl.NotMatchPassword;
import com.dayone.model.Auth;
import com.dayone.persist.MemberRepository;
import com.dayone.persist.entity.MemberEntity;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class MemberService implements UserDetailsService {

    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return this.memberRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("couldn't find user -> " + username));
    }

    public MemberEntity register(Auth.SignUp member) {
        // 아이디가 존재하는 경우 exception 발생
        boolean exists = memberRepository.existsByUsername(member.getUsername());
        if (exists) {
            throw new AlreadyExistUserException();
        }

        // ID 생성 가능한 경우, 멤버 테이블에 저장
        // 비밀번호는 암호화 되어서 저장되어야함
        member.setPassword(this.passwordEncoder.encode(member.getPassword()));
        MemberEntity memberEntity = this.memberRepository.save(member.toEntity());
        return memberEntity;
    }

    public MemberEntity authenticate(Auth.SignIn member) {
        // id 로 멤버 조회
        MemberEntity memberEntity = this.memberRepository.findByUsername(member.getUsername())
                .orElseThrow(() -> new RuntimeException("존재하지 않는 ID 입니다."));
        // 패스워드 일치 여부 확인
        if (!this.passwordEncoder.matches(member.getPassword(), memberEntity.getPassword())){
            // - 일치하지 않는 경우 400 status 코드와 적합한 에러 메시지 반환
            throw new NotMatchPassword();
        }
        // - 일치하는 경우, 해당 멤버 엔티티 반환
        return memberEntity;
    }
}
