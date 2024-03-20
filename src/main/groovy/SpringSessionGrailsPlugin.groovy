import grails.plugins.Plugin
import groovy.util.logging.Slf4j
import org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration
import springsession.config.SpringSessionConfig
import springsession.config.MasterNamedNode
import springsession.config.NoOpConfigureRedisAction
import springsession.http.HttpSessionSynchronizer
import org.springframework.data.redis.connection.RedisNode
import org.springframework.data.redis.connection.RedisSentinelConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.session.data.redis.RedisOperationsSessionRepository
import org.springframework.session.web.http.CookieHttpSessionStrategy
import org.springframework.session.web.http.HeaderHttpSessionStrategy
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisShardInfo
import utils.SpringSessionUtils

@Slf4j
class SpringSessionGrailsPlugin extends Plugin {

    def version = "3.3.10"
    def grailsVersion = "3.3.0 > *"
    def title = "Spring Session Grails Plugin"
    def author = "Jitendra Singh"
    def authorEmail = "jeet.mp3@gmail.com"
    def description = 'Provides support for SpringSession project'
    def documentation = "https://github.com/jeetmp3/spring-session"
    def license = "APACHE"
    def issueManagement = [url: "https://github.com/jeetmp3/spring-session/issues"]
    def scm = [url: "https://github.com/jeetmp3/spring-session"]
    def loadAfter = ['springSecurityCore', 'cors']
    def profiles = ['web']

    Closure doWithSpring() {
        def beans = { ->
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
                        poolConfig.maxTotal = conf.redis.poolConfig.maxTotal
                        poolConfig.maxIdle = conf.redis.poolConfig.maxIdle
                        poolConfig.minIdle = conf.redis.poolConfig.minIdle
                        log.info("Redis Pool Setting: maxTotal: " + poolConfig.maxTotal + ", maxIdle: " + poolConfig.maxIdle + ", minIdle: " + poolConfig.minIdle)
                    }
                    convertPipelineAndTxResults = conf.redis.connectionFactory.convertPipelineAndTxResults
                }
            }

            sessionRedisTemplate(RedisTemplate) { bean ->
                keySerializer = ref("stringRedisSerializer")
                hashKeySerializer = ref("stringRedisSerializer")
                connectionFactory = ref("redisConnectionFactory")
                defaultSerializer = ref("jdkSerializationRedisSerializer")
                bean.initMethod = "afterPropertiesSet"
            }

            redisHttpSessionConfiguration(RedisHttpSessionConfiguration) {
                maxInactiveIntervalInSeconds = conf.maxInactiveInterval
            }

            String defaultStrategy = conf.strategy.defaultStrategy
            if (defaultStrategy == "HEADER") {
                httpSessionStrategy(HeaderHttpSessionStrategy) {
                    headerName = conf.strategy.httpHeader.headerName
                }
            } else {
                httpSessionStrategy(CookieHttpSessionStrategy) {
                    cookieSerializer {
                        cookieName = conf.strategy.cookie.name
                    }
                }
            }

            configureRedisAction(NoOpConfigureRedisAction)

            // HttpSessionSynchronizer
            httpSessionSynchronizer(HttpSessionSynchronizer) {
                persistMutable = conf.allow.persist.mutable as Boolean
            }

            log.info 'Finished Spring Session configuration'
        }
    }
}
