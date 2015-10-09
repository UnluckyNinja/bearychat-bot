package ninja.unlucky.bearychatbot

import groovy.util.logging.Log4j2
import groovy.json.*

import io.vertx.lang.groovy.GroovyVerticle
import io.vertx.core.Future

@Log4j2
public class BearychatBot extends GroovyVerticle {

    def jsonSluper
    public void start(Future<Void> fut) {
        log.info 'Starting'
        this.jsonSluper = new JsonSlurper()
        vertx.createHttpServer().requestHandler{ req ->
            println 'request received!'
            def json
            req.bodyHandler{
                json = jsonSluper.parseText it.toString("UTF-8")?:'{}'
            
                log.debug json
                req.response().with{
                    putHeader 'content-type', 'application/json'
                    end json?JsonOutput.toJson([text: json.text]):''
                }
            }
            log.debug 'Headers: '
            log.debug ({
                def form = req.headers()
                def set = form.names()
                set.collectEntries{
                    [(it): form.getAll(it)]
                }
            }())
            log.debug req.remoteAddress().with{ "Request from ${host()}:${port()}" }
            log.debug "Request for ${req.absoluteURI()}"
        }
        .listen 80, { result ->
            if (result.succeeded()) {
                fut.complete()
            } else {
                fut.fail result.cause()
            }
        }
        log.info 'Started'
    }
}
