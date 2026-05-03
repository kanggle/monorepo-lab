package com.example.fanplatform.community.infrastructure.membership;

import com.example.fanplatform.community.domain.membership.MembershipChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MembershipCheckerAutoConfig {

    @Bean
    @ConditionalOnMissingBean(MembershipChecker.class)
    public MembershipChecker defaultMembershipChecker() {
        return new AlwaysAllowMembershipChecker();
    }
}
