package ninja.unlucky.bearychatbot

import groovy.util.logging.Log4j2

import io.vertx.lang.groovy.GroovyVerticle
import io.vertx.core.Future

@Log4j2
public class BearychatBot extends GroovyVerticle {

    public void start(Future<Void> fut) {
        log.info 'Starting'
        vertx.createHttpServer().requestHandler{ req ->
            println 'request received!'
            req.response().with{
                putHeader 'content-type', 'text/plain'
                end 'vert.x 3 speaking'
            }
            log.debug 'Attributes: '
            log.debug ({
                def form = req.formAttributes()
                def set = form.names()
                set.collect{
                    form.getAll(it)
                }
            }())
            log.debug 'Params: '
            log.debug ({
                def form = req.params()
                def set = form.names()
                set.collect{
                    form.getAll(it)
                }
            }())
            log.debug req.localAddress().with{ "Request from ${host()}:${port()}" }
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