import grails.plugins.Plugin
import groovy.util.logging.Slf4j
import io.micronaut.spring.core.event.ApplicationEventPublisherAdapter
import org.apache.catalina.core.ApplicationContext
import org.grails.plugins.springsession.config.SpringSessionConfig
import org.grails.plugins.springsession.data.redis.config.MasterNamedNode
import org.grails.plugins.springsession.data.redis.config.NoOpConfigureRedisAction
import org.grails.plugins.springsession.web.http.HttpSessionSynchronizer
import org.springframework.data.redis.connection.RedisNode
import org.springframework.data.redis.connection.RedisSentinelConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.session.web.http.CookieHttpSessionIdResolver
import org.springframework.session.web.http.DefaultCookieSerializer
import org.springframework.session.web.http.HeaderHttpSessionIdResolver
import redis.clients.jedis.JedisPoolConfig
import utils.SpringSessionUtils
import org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration;
@Slf4j
class SpringSessionGrailsPlugin extends Plugin {

    def version = "5.3.7"
    def grailsVersion = "5.3.5 > *"
    def title = "Spring Session Grails Plugin"
    def author = "Jitendra Singh | Modified by Yaji Lin"
    def authorEmail = "jeet.mp3@gmail.com;yajilin@fontrip.com"
    def description = 'Provides support for SpringSession project'
    def documentation = "https://github.com/jeetmp3/spring-session"
    def license = "APACHE"
    def issueManagement = [url: "https://github.com/jeetmp3/spring-session/issues"]
    def scm = [url: "https://github.com/jeetmp3/spring-session"]
    def loadAfter = ['springSecurityCore', 'cors']
    def profiles = ['web']

    Closure doWithSpring() {
        def beans = {
            log.info 'Configuring Spring session'
            SpringSessionUtils.application = grailsApplication
            ConfigObject conf = SpringSessionUtils.sessionConfig

            if (!conf || !conf.active) {
                log.warn 'Spring session is disabled, not loading'
                return
            }

            springSessionConfig(SpringSessionConfig) {
                grailsApplication = grailsApplication
            }



            if (conf.redis.sentinel.master && conf.redis.sentinel.nodes) {
                List<Map> nodes = conf.redis.sentinel.nodes as List<Map>
                masterName(MasterNamedNode) {
                    name = conf.redis.sentinel.master
                }
                shardInfo(JedisShardInfo, conf.redis.connectionFactory.hostName, conf.redis.connectionFactory.port) {
                    password = conf.redis.sentinel.password ?: null
                    timeout = conf.redis.sentinel.timeout ?: 5000
                }
                redisSentinelConfiguration(RedisSentinelConfiguration) {
                    master = ref("masterName")
                    sentinels = (nodes.collect { new RedisNode(it.host as String, it.port as Integer) }) as Set
                }
                redisConnectionFactory(JedisConnectionFactory, ref("redisSentinelConfiguration"), ref("poolConfig")) {
                    shardInfo = ref("shardInfo")
                    usePool = conf.redis.connectionFactory.usePool
                }
            } else {
                // Redis Connection Factory Default is JedisConnectionFactory
                redisConnectionFactory(JedisConnectionFactory) {
                    hostName = conf.redis.connectionFactory.hostName ?: "localhost"
                    port = conf.redis.connectionFactory.port ?: 6379
                    timeout = conf.redis.connectionFactory.timeout ?: 2000
                    usePool = conf.redis.connectionFactory.usePool ?: false
                    database = conf.redis.connectionFactory.dbIndex ?:0
                    if (conf.redis.connectionFactory.password) {
                        password = conf.redis.connectionFactory.password
                    }
                    log.info("Redis Setting timeout: " + timeout)
                    if (usePool) {
                        poolConfig = new JedisPoolConfig()
                        poolConfig.maxTotal = conf.redis.connectionFactory.pool.maxTotal
                        poolConfig.maxIdle = conf.redis.connectionFactory.pool.maxIdle
                        poolConfig.minIdle = conf.redis.connectionFactory.pool.minIdle
                        log.info("Redis Pool Setting: maxTotal: " + poolConfig.maxTotal + ", maxIdle: " + poolConfig.maxIdle + ", minIdle: " + poolConfig.minIdle)
                    }
                    convertPipelineAndTxResults = conf.redis.connectionFactory.convertPipelineAndTxResults
                }
            }

            redisHttpSessionConfiguration(RedisHttpSessionConfiguration) {
                maxInactiveIntervalInSeconds = conf.maxInactiveInterval
            }


            redisTemplate(RedisTemplate) { bean ->
                keySerializer = ref("stringRedisSerializer")
                hashKeySerializer = ref("stringRedisSerializer")
                connectionFactory = ref("redisConnectionFactory")
                defaultSerializer = ref("jdkSerializationRedisSerializer")
                bean.initMethod = "afterPropertiesSet"
            }

            cookieSerializer(DefaultCookieSerializer) {
                cookieName = conf.strategy.cookie.name ?: 'SESSION'
                sameSite = conf.strategy.cookie.sameSite ?: 'Lax'
                useSecureCookie = conf.strategy.cookie.secure ?: true
                useHttpOnlyCookie = conf.strategy.cookie.httpOnly ?: true
            }

            String defaultStrategy = conf.strategy.defaultStrategy
            if (defaultStrategy == "HEADER") {
                httpSessionIdResolver(HeaderHttpSessionIdResolver) {
                    headerName = conf.strategy.httpHeader.headerName
                }
            } else {
                httpSessionIdResolver(CookieHttpSessionIdResolver) {
                    cookieSerializer = ref("cookieSerializer")
                }
            }

            configureRedisAction(NoOpConfigureRedisAction)
            httpSessionSynchronizer(HttpSessionSynchronizer) {
                persistMutable = conf.allow.persist.mutable as Boolean
            }


            log.info 'Finished Spring Session configuration'
        }
        return beans
    }
}
