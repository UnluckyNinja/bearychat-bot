package ninja.unlucky.bearychatbot.command

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Created by UnluckyNinja on 2015/10/18.
 */
class PacktpubCommand implements CommandExecutor {

    @Command(name = 'ebook')
    @WebAccessor(type = 'get', port = 443, host = 'https://www.packtpub.com', requestURI = '/packt/offers/free-learning')
    Map freeEbook(Map map, Document doc) {

        def text = ""
        def items = []
        doc.select('div.dotd-main-book.cf').first().children().each { Element child ->
            def item = [:]
            def name = child.select('div.dotd-title').first().text()
            log.debug child.select('div.dotd-main-book-image.float-left').first().select('a').first().attr('abs:href')
            def (booklink, imagelink) = child.select('div.dotd-main-book-image.float-left').first().select('a').first().with {
                [attr('abs:href'), select('img').attr('abs:src')]
            }
            def color = '#D92238'
            def description = child.select('div.dotd-main-book-summary.float-left').first().children().select('div').with {
                delegate[2].text() + '\n' + (delegate[3].select('ul')?.first()?.children()?.collect {
                    '* ' + it.text()
                }?.join('\n') ?: '')
            }
            def claimlink = child.select('a.twelve-days-claim').attr('abs:href')
            text = "**Packtpub Free Ebook**\n[$name]($booklink)"
            item.title = name
            item.text = "$description\n[Click here to claim this ebook(signin first if not)]($claimlink)"
            item.color = color
            item.images = [[url: imagelink]]
            items << item
        }
        [text: text, attachments: items]
    }

}
