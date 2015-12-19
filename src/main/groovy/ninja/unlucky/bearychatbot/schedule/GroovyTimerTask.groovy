package ninja.unlucky.bearychatbot.schedule
/**
 * Created by UnluckyNinja on 2015/12/19.
 */
class GroovyTimerTask extends TimerTask {

    Closure closure

    GroovyTimerTask() {}

    GroovyTimerTask(Closure closure) {
        this.closure = closure
    }

    @Override
    void run() {
        closure.call()
    }

    GroovyTimerTask run(Closure c){
        this.closure = c
        return this
    }
}
