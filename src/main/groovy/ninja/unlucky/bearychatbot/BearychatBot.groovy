package ninja.unlucky.bearychatbot

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j2
import io.vertx.core.Future
import io.vertx.groovy.core.buffer.Buffer
import io.vertx.lang.groovy.GroovyVerticle
import org.jsoup.Jsoup

@Log4j2
public class BearychatBot extends GroovyVerticle {

    def jsonSluper
    def server
    def client

    public void start(Future<Void> fut) {
        log.info 'Starting'
        this.jsonSluper = new JsonSlurper()
        this.server = vertx.createHttpServer(compressionSupported: true)
        this.client = vertx.createHttpClient(ssl: true, trustAll: true, tryUseCompression: true)
        setupServer(server, fut)
        //testClient(client)
        log.info 'Started'
    }
    def cookie = ''

    def setupServer(server, fut) {
        server.requestHandler { req ->
            println 'request received!'
            def json
            req.bodyHandler { req_buffer ->
                json = jsonSluper.parseText req_buffer.toString("UTF-8") ?: '{}'
                if (json) log.debug json
                if (!json || json.subdomain != 'craft_lamplighter') {
                    req.response().with {
                        putHeader 'Content-Type', 'application/json'
                        end '{text="access denied"}'
                    }
                    log.debug 'access denied'
                    return
                }
                def options = json.text.split('\\s').tail()
                if (options[0] == 'steam') {
                    this.client.get(443, 'steamdb.info', '/sales/') { c_res ->
                        debugResponse(c_res)
                        if (!cookie && c_res.cookies()) {
                            cookie = c_res.cookies().join(' ')
                        }
                        c_res.bodyHandler { c_res_buffer ->
                            def pageString = c_res_buffer.toString("UTF-8")
                            def page = Jsoup.parse(pageString)
                            log.debug pageString.size()

                            def items = []
                            def sales = page.select('tbody[data-section="dailydeal"]').first().children().each { child ->
                                def item = [:]
                                def (name, link) = child.select('a.b').first().with {
                                    [text(), "https://steampowered.com${attr('href')}"]
                                }
                                def discount = child.select('td[class^=price-discount]').first().text()
                                def color = '#8BC34A'
                                def lowest = child.select('span.lowest-discount').with {
                                    if (isEmpty()) return discount
                                    color = '#9E9E9E'
                                    first().select('b').text()
                                }
                                def price = child.select('td.price-final').text()
                                def logo = child.select('td.applogo').select('a').first().attr('href').replaceFirst($/(/(app|sub))(/\d+/)/$) {
                                    it[1] + 's' + it[3]
                                }
                                def logolink = "https://steamcdn-a.akamaihd.net/steam${logo}capsule_sm_120.jpg"
                                def timeleft = child.select('td.timeago').text()
                                def rating = child.select('span.tooltipped').text()
                                def title = [name, price, "$discount/$lowest", rating].join(' ')
                                item.title = title
                                item.text = link
                                item.color = color
                                item.images = [[url: logolink]]
                                items << item
                            }
                            req.response().with {
                                def jsonOutput = JsonOutput.toJson([text: 'Steam Daily Deals', attachments: items])
                                log.debug jsonOutput//JsonOutput.prettyPrint(jsonOutput)
                                def buffer = Buffer.buffer(jsonOutput, 'UTF-8')
                                putHeader 'Content-Type', 'application/json'
                                putHeader 'Content-Length', ''+buffer.length()
                                end buffer
                            }
                        }.exceptionHandler { e ->
                            println e
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
                        putHeader 'referer', 'https://steamdb.info/'
                        putHeader 'upgrade-insecure-requests', '1'
                        putHeader 'user-agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.101 Safari/537.36'
                    }.end()
                } else if (options[0] == 'ping') {
                    req.response().with {
                        def jsonOutput = JsonOutput.toJson([text: 'Pong!'])
                        log.debug jsonOutput//JsonOutput.prettyPrint(jsonOutput)
                        def buffer = Buffer.buffer(jsonOutput, 'UTF-8')
                        putHeader 'Content-Type', 'application/json'
                        putHeader 'Content-Length', buffer.length()
                        end buffer
                    }
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
