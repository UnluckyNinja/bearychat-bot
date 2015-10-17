package ninja.unlucky.bearychatbot.command

import groovy.util.logging.Log4j2
import org.jsoup.nodes.Document

/**
 * Created by UnluckyNinja on 2015/10/17.
 */
@Log4j2
class SteamCommand implements CommandExecutor {

    @Command(name = 'steam')
    @WebAccessor(port = 443, host = 'https://steamdb.info', requestURI = '/sales/')
    Map requestDailyDeals(Map received, Document doc) {
        def text = "Steam Daily Deals"
        def items = []
        doc.select('tbody[data-section="dailydeal"]').first().children().each { child ->
            def item = [:]
            def (name, link) = child.select('a.b').first().with {
                [delegate.text(), "https://www.steampowered.com${attr('href')}"]
            }
            def discount = child.select('td[class^=price-discount]').first().text()
            def color = '#8BC34A'
            def lowest = child.select('span.lowest-discount').with {
                if (isEmpty()) return discount
                color = '#9E9E9E'
                first().select('b').text()
            }
            def price = child.select('td[class^=price-discount] ~ td').first().text()
            def logo = child.select('td.applogo').select('a').first().attr('href').replaceFirst($/(/(app|sub))(/\d+/)/$) {
                it[1] + 's' + it[3]
            }
            def logolink = "https://steamcdn-a.akamaihd.net/steam${logo}capsule_sm_120.jpg"
            def timeleft = child.select('td.timeago').text()
            def rating = child.select('span.tooltipped').text()
            def title = [name, price, "$discount/$lowest", "Rating:$rating"].join(' ')
            item.title = title
            item.text = link
            item.color = color
            item.images = [[url: logolink]]
            items << item

        }
        [text: text, attachments: items]
    }
}
