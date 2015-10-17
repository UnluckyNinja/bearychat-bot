package ninja.unlucky.bearychatbot.command

/**
 * Created by UnluckyNinja on 2015/10/18.
 */
class PingCommand implements CommandExecutor{
    @Command(name='ping')
    Map ping(Map map){
        return [text: 'Pong!']
    }
}
