package ninja.unlucky.bearychatbot

import io.vertx.core.Future
import io.vertx.groovy.core.buffer.Buffer
import io.vertx.groovy.core.http.*
import io.vertx.lang.groovy.GroovyVerticle
import ninja.unlucky.bearychatbot.command.*
import ninja.unlucky.bearychatbot.schedule.GroovyTimerTask
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import java.time.LocalDateTime
import java.time.ZoneId

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j2

@Log4j2
public class BearychatBot extends GroovyVerticle {

    JsonSlurper jsonSluper
    HttpServer server
    HttpClient client
    HttpClient timerClient

    Timer timer = new Timer(true)

    private Map<String, Closure> commands = [:].asSynchronized()
    private Map<String, WebAccessor> accessors = [:].asSynchronized()
    private Map<String, String> cookies = [:].asSynchronized()
    private synchronized LocalDateTime dateTime
    private String hookURI

    public void start(Future<Void> fut) {
        hookURI = new File('hook.txt').text
        log.info 'Starting'
        this.jsonSluper = new JsonSlurper()
        this.server = vertx.createHttpServer(compressionSupported: true)
        this.client = vertx.createHttpClient(ssl: true, trustAll: true, tryUseCompression: true)
        this.timerClient = vertx.createHttpClient(ssl: true, trustAll: true, tryUseCompression: true)
        registerCommands(SteamCommand, PingCommand, PacktpubCommand, TimeCommand)
        setupServer(server, fut)

        timer = new Timer(true)
        setupScheduleTask(timer, client);

        log.info 'Started'
    }

    void setupServer(server, fut) {
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
                if (options[0] != 'bot' || options.size() < 2) {
                    req.response().close()
                    return
                }
                options = options.tail()
                processServerRequest(req, options);
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

    void setupScheduleTask(Timer timer, HttpClient client) {

        this.dateTime = LocalDateTime.now().withHour(13)

        def task = new GroovyTimerTask().run {
            if (dateTime <= LocalDateTime.now()) {
                accessWeb(accessors.steam, cookies.steam) {
                    Map map = commands.steam.call(['steam'], it)
                    hookPush(map) { c_res ->
                        log.info "Steam task sent at ${LocalDateTime.now().toString()}!"
                    }
                }
                accessWeb(accessors.ebook, cookies.ebook) {
                    Map map = commands.ebook.call(['ebook'], it)
                    hookPush(map) { c_res ->
                        log.info "Ebook task sent at ${LocalDateTime.now().toString()}!"
                    }
                }
                dateTime = dateTime.plusDays(1)
            }
        }


        if (dateTime < LocalDateTime.now()) {
            dateTime = dateTime.plusDays(1)
        }

        timer.scheduleAtFixedRate(task, Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()), 24 * 60 * 60 * 1000)
        dateTime.minusSeconds(1)
    }

    void hookPush(int port, String host, String hookURI, Map map, Closure c = null) {
        this.timerClient.post(443, 'hook.bearychat.com', hookURI) { c_res ->
            if (c) {
                if (c.maximumNumberOfParameters > 0) {
                    c.call(c_res)
                } else {
                    c.call()
                }
            }
        }.putHeader('Content-Type', 'application/json')
                .end(JsonOutput.toJson(map))
    }

    void hookPush(Map map, Closure c = null) {
        hookPush(443, 'hook.bearychat.com', hookURI, map, c)
    }

    HttpServerRequest processServerRequest(HttpServerRequest req, String[] options) {
        def method = commands.get(options[0])
        def accessor = accessors.get(options[0])
        def cookie = cookies.get(options[0])

        // check method availability
        if (method) {
            if (accessor) {

                // do the request
                this.client."${accessor.type()}"(accessor.port(), accessor.host().replaceFirst(/https?:\/\//, ''), accessor.requestURI()) { HttpClientResponse c_res ->
                    debugResponse(c_res)
                    if (c_res.cookies()) {
                        cookie = c_res.cookies().collect { it.split(';\\s') }.flatten().unique().join('; ')
                        cookies.put(options[0], cookie)
                    }
                    c_res.bodyHandler { c_res_buffer ->
                        def pageString = c_res_buffer.toString("UTF-8")
                        def page = Jsoup.parse(pageString, accessor.host())
                        log.debug pageString.size()

                        Map result = method(options, page) as Map // the working method

                        setResponse(req, result) // end response
                    }.exceptionHandler { e ->
                        log.warn e
                        req.response().with {
                            putHeader 'Content-Type', 'application/json'
                            end JsonOutput.toJson([text: '查询失败'])
                        }
                    }
                }.with {
                    setHeader(it, cookie)
                }.end()
            } else { // if no need to access web
                Map result = method(options) as Map

                setResponse(req, result)
            }
        } else {
            setResponse(req, [text: "未设置 '${options[0]}' 命令"])
        }
    }

    private void accessWeb(WebAccessor accessor, String cookie, Closure callback) {
        this.timerClient."${accessor.type()}"(accessor.port(), accessor.host().replaceFirst(/https?:\/\//, ''), accessor.requestURI()) { HttpClientResponse c_res ->
            debugResponse(c_res)
            if (c_res.cookies()) {
                cookie = c_res.cookies().collect { it.split(';\\s') }.flatten().unique().join('; ')
            }
            c_res.bodyHandler { c_res_buffer ->
                def pageString = c_res_buffer.toString("UTF-8")
                def page = Jsoup.parse(pageString, accessor.host())
                log.debug pageString.size()

                Map result = callback(page) as Map // the working method

            }.exceptionHandler { e ->
                log.warn e
            }
        }.with {
            setHeader(it, cookie)
        }.end()
    }

    private void setResponse(HttpServerRequest req, Map json) {
        req.response().with {
            String jsonOutput = JsonOutput.toJson(json)
            log.debug jsonOutput.length() //JsonOutput.prettyPrint(jsonOutput)
            def buffer = Buffer.buffer(jsonOutput, 'UTF-8')
            putHeader 'Content-Type', 'application/json'
            putHeader 'Content-Length', '' + buffer.length()
            end buffer
        }
    }

    private void setHeader(HttpClientRequest req, String cookie) {
        req.with {
            putHeader 'accept', 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8'
            putHeader 'accept-encoding', 'gzip, deflate, sdch'
            putHeader 'accept-language', 'zh-CN,zh;q=0.8,en;q=0.6,zh-TW;q=0.4,ja;q=0.2'
            putHeader 'cache-control', 'max-age=0'
            if (cookie) {
                putHeader 'cookie', cookie
            }
            putHeader 'dnt', '1'
            //putHeader 'referer', accessor.host()
            putHeader 'upgrade-insecure-requests', '1'
            putHeader 'user-agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.101 Safari/537.36'
        }
    }

    void registerCommands(Class<? extends CommandExecutor> clazz) {
        def methods = clazz.methods.findAll {
            it.getAnnotation(Command) != null &&
                    (it.parameters.size() == 2 && it.getAnnotation(WebAccessor) != null
                            && it.parameterTypes[0].with { isArray() && getComponentType() == String }
                            && it.parameterTypes[1] == Document
                            ||
                            it.parameters.size() == 1
                            && it.parameterTypes[0].with { isArray() && getComponentType() == String })
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
