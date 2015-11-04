package ninja.unlucky.bearychatbot

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j2
import io.vertx.core.Future
import io.vertx.groovy.core.buffer.Buffer
import io.vertx.groovy.core.http.HttpClient
import io.vertx.groovy.core.http.HttpServer
import io.vertx.groovy.core.http.HttpServerRequest
import io.vertx.lang.groovy.GroovyVerticle
import ninja.unlucky.bearychatbot.command.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Log4j2
public class BearychatBot extends GroovyVerticle {

    JsonSlurper jsonSluper
    HttpServer server
    HttpClient client
    private Map<String, Closure> commands = [:].asSynchronized()
    private Map<String, WebAccessor> accessors = [:].asSynchronized()
    private Map<String, String> cookies = [:].asSynchronized()

    public void start(Future<Void> fut) {
        log.info 'Starting'
        this.jsonSluper = new JsonSlurper()
        this.server = vertx.createHttpServer(compressionSupported: true)
        this.client = vertx.createHttpClient(ssl: true, trustAll: true, tryUseCompression: true)
        registerCommands(SteamCommand, PingCommand, PacktpubCommand)
        setupServer(server, fut)
        testClient(client)
        log.info 'Started'
    }

    def setupServer(server, fut) {
        server.requestHandler { req ->
            log.debug 'Request received!'
            req.bodyHandler { req_buffer ->
                def json
                if (req.getHeader('content-type') == 'application/json') {
                    json = jsonSluper.parseText(req_buffer.toString("UTF-8") ?: '{}')
                }
                if (json) log.debug json
                if (!json || json.subdomain != 'craft_lamplighter') {
                    req.response().with {
                        putHeader 'Content-Type', 'application/json'
                        end '{text: "access denied"}'
                    }
                    log.debug 'access denied'
                    return
                }
                String[] options = json.text.split('\\s')
                if(options[0] != 'bot' || options.size() < 2){
                    req.response().close()
                    return
                }
                options = options.tail()
                def method = commands.get(options[0])
                def accessor = accessors.get(options[0])
                def cookie = cookies.get(options[0])
                if (method) {
                    if (accessor) {
                        this.client."${accessor.type()}"(accessor.port(), accessor.host().replaceFirst(/https?:\/\//, ''), accessor.requestURI()) { c_res ->
                            debugResponse(c_res)
                            if (c_res.cookies()) {
                                cookie = c_res.cookies().collect { it.split(';\\s') }.flatten().unique().join('; ')
                                cookies.put(options[0], cookie)
                            }
                            c_res.bodyHandler { c_res_buffer ->
                                def pageString = c_res_buffer.toString("UTF-8")
                                def page = Jsoup.parse(pageString, accessor.host())
                                log.debug pageString.size()
                                Map result = method(json, page) as Map // the working method

                                setResponse(req, result)
                            }.exceptionHandler { e ->
                                log.warn e
                                req.response().with {
                                    putHeader 'Content-Type', 'application/json'
                                    end JsonOutput.toJson([text: '查询失败'])
                                }
                            }
                        }.with {
                            putHeader 'accept', 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8'
                            putHeader 'accept-encoding', 'gzip, deflate, sdch'
                            putHeader 'accept-language', 'zh-CN,zh;q=0.8,en;q=0.6,zh-TW;q=0.4,ja;q=0.2'
                            putHeader 'cache-control', 'max-age=0'
                            if (cookie) {
                                putHeader 'cookie', cookie
                            }
                            putHeader 'dnt', '1'
                            putHeader 'referer', accessor.host()
                            putHeader 'upgrade-insecure-requests', '1'
                            putHeader 'user-agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.101 Safari/537.36'
                        }.end()
                    } else {
                        Map result = method(json) as Map

                        setResponse(req, result)
                    }
                } else {
                    setResponse(req, [text: "未设置 '${options[0]}' 命令"])
                }
            }
            debugRequest(req)
        }.listen 80, { result ->
            if (result.succeeded()) {
                fut.complete()
            } else {
                fut.fail result.cause()
            }
        }
    }

    private void setResponse(HttpServerRequest req, Map json) {
        req.response().with {
            def jsonOutput = JsonOutput.toJson(json)
            log.debug jsonOutput//JsonOutput.prettyPrint(jsonOutput)
            def buffer = Buffer.buffer(jsonOutput, 'UTF-8')
            putHeader 'Content-Type', 'application/json'
            putHeader 'Content-Length', '' + buffer.length()
            end buffer
        }
    }

    void registerCommands(Class<? extends CommandExecutor> clazz) {
        def methods = clazz.methods.findAll {
            it.getAnnotation(Command) != null &&
                    (it.parameters.size() == 2
                            && it.getAnnotation(WebAccessor) != null
                            && it.parameterTypes as List == [Map, Document]
                            ||
                            it.parameters.size() == 1
                            && it.parameterTypes as List == [Map])
        }

        if (!methods) {
            throw new NoSuchMethodException("Class($clazz.name) you want to register doesn't have any satisfied method.")
        }
        def instance = clazz.newInstance()

        methods.each {
            def accessor = it.getAnnotation(WebAccessor)
            def name = it.getAnnotation(Command).name()
            this.commands.put(name, it.&invoke.curry(instance))
            if (accessor) this.accessors.put(name, accessor)
        }
    }

    void registerCommands(Class<? extends CommandExecutor>[] classes) {
        classes.each { registerCommands(it) }
    }

    def testClient(client) {

    }

    def debugRequest(req) {
        log.debug req.remoteAddress().with { "Request from ${host()}:${port()}" }
        log.debug "Request for ${req.absoluteURI()}"
        log.debug 'Headers: '
        log.debug({
            def form = req.headers()
            def set = form.names()
            set.collectEntries {
                [(it): form.getAll(it)]
            }
        }())
    }

    def debugResponse(res) {
        log.debug res.with { "Response back ${statusCode()}:${statusMessage()}" }
        log.debug 'Headers: '
        log.debug({
            def form = res.headers()
            def set = form.names()
            set.collectEntries {
                [(it): form.getAll(it)]
            }
        }())
    }
}
