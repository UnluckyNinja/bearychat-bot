package ninja.unlucky.bearychatbot.command

import groovy.util.logging.Log4j2

/**
 * Created by UnluckyNinja on 2015/10/18.
 */
@Log4j2
class PingCommand implements CommandExecutor{
    @Command(name='ping')
    Map ping(Map map){
        return [text: 'Pong!']
    }
}
