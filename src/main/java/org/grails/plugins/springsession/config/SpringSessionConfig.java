package org.grails.plugins.springsession.config;

import grails.core.GrailsApplication;
import org.grails.plugins.springsession.converters.GrailsJdkSerializationRedisSerializer;
import org.grails.plugins.springsession.web.http.HttpSessionSynchronizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.SessionRepositoryFilter;
import redis.clients.jedis.JedisPoolConfig;
@EnableRedisHttpSession
@Configuration
public class SpringSessionConfig {
    private GrailsApplication grailsApplication;
    private JedisConnectionFactory redisConnectionFactory;
    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
    }

    @Bean
    public RedisSerializer jdkSerializationRedisSerializer() {
        return new GrailsJdkSerializationRedisSerializer(grailsApplication);
    }

    @Bean
    public StringRedisSerializer stringRedisSerializer() {
        return new StringRedisSerializer();
    }

    @Bean
    public JedisPoolConfig poolConfig() {
        return new JedisPoolConfig();
    }

    @Bean
    public FilterRegistrationBean springSessionFilter(SessionRepositoryFilter filter) {
        FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        registrationBean.setFilter(filter);
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registrationBean;
    }
}
