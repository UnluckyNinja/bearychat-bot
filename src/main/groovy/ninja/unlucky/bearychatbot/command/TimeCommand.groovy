package ninja.unlucky.bearychatbot.command

import ninja.unlucky.bearychatbot.BearychatBot
import ninja.unlucky.bearychatbot.schedule.GroovyTimerTask

import java.time.LocalDateTime

import groovy.util.logging.Log4j2

/**
 * Created by UnluckyNinja on 2015/12/20.
 */
@Log4j2
class TimeCommand implements CommandExecutor {
    BearychatBot bot

    final String ERROR1 = '命令参数错误'

    TimeCommand(BearychatBot bot) {
        this.bot = bot
    }

    @Command(name = 'time')
    Map requestDailyDeals(final String[] options) {
        def text = 'NOT_SET_YET'
        if(options.size() == 1){
            return [text: LocalDateTime.now().toString()]
        }

        if(options[1] == 'schedule' && options.size() >= 5){
            if(options[3] == '-p'){
                def delay
                try {
                    delay = options[2].toFloat()
                }catch (NumberFormatException e){
                    return fail('延时数值格式错误')
                }
                delay = Math.round(delay*10) * 100
                if (delay < 1000 || delay > 1000 * 60){
                    return fail('延时需要在范围[1, 60]秒以内')
                }
                def msg = options.drop(4).join(' ')
                def task = new GroovyTimerTask().run {
                    bot.hookPush([text: LocalDateTime.now().toString()]){
                        log.info "TimeCommand Executed at ${LocalDateTime.now().toString()}"
                    }
                }
                bot.timer.schedule(task, delay)
                text = "定时任务已设置，将在${delay/1000}秒后执行"
            }else{
                return fail('暂只支持以下选项：\n -p')
            }
        } else {
            return fail(ERROR1)
        }

        [text: text]
    }

    Map fail(String msg){
        return [text: msg]
    }
}
