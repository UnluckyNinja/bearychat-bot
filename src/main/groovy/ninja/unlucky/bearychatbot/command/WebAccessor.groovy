package ninja.unlucky.bearychatbot.command

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Created by UnluckyNinja on 2015/10/17.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@interface WebAccessor {
    String type() default 'get'
    int port()
    String host()
    String requestURI() default '/'
}