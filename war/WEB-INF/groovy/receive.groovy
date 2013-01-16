import groovy.json.JsonSlurper
import groovy.xml.MarkupBuilder
import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.urlfetch.HTTPResponse
import java.util.concurrent.Future
import com.google.appengine.api.datastore.Text

def jsonPost = params['payload']

log.info "JSON payload: <<<\n ${jsonPost} \n>>>"

def entity = new Entity('jsonPost')
entity.unindexed.payload = new Text(jsonPost)
entity.remoteAddr = request.remoteAddr
entity.when = new Date()
entity.save()

log.info "JSON payload saved in datastore"

def slurper = new JsonSlurper()
def summary = slurper.parseText(jsonPost)

log.info "JSON payload parsed"

def repoName = summary.repository.name
def ownerName = summary.repository.owner.name

def commits = summary.commits.collect { commit ->
    def commitUrl = "https://api.github.com/repos/${ownerName}/${repoName}/commits/${commit.id}"
    new URL(commitUrl).get(async: true)
}.collect { Future<HTTPResponse> future ->
    slurper.parseText(new String(future.get().content, 'UTF-8'))
}

log.info "Each commit parsed"

String linkGroovyIssues(String msg) {    
    msg.replaceAll(/(GROOVY-\d{1,4})/, '<a href="http://jira.codehaus.org/browse/$1">$1</a>')
}

def subject = "${ownerName}/${repoName}: ${summary.commits.size()} commit${summary.commits.size() > 1 ? 's' : ''} on ${summary.ref}"

def styles = [
        summary: 'margin-bottom: 30px;',
        commit: 'margin-top: 10px; margin-bottom: 20px;',
        commitDetails: 'border: 4px solid white; background-color: #e0ffff; padding: 10px;',
        file: 'margin: 10px 0 10px 10px; padding: 5px; border: 2px solid #d3d3d3; background-color: #fafad2;',
        filename: 'font-family: monospace; font-size: larger; font-weight: bold;',
        patch: 'margin-left: 40px;',
        diffPosition: 'white-space: pre; font-family: monospace; display: block; margin-top: 5px; margin-bottom: 5px; padding-top: 5px; padding-bottom: 5px; border-top: 1px solid gray; background-color: lightgray;',
        diffAddition: 'white-space: pre; font-family: monospace; display: block; background-color: #CCFFCC;',
        diffDeletion: 'white-space: pre; font-family: monospace; display: block; background-color: #FFCCCC;',
        diffNormal: 'white-space: pre; font-family: monospace; display: block;'
]

def stringWriter = new StringWriter()
new MarkupBuilder(stringWriter).html {
    head {
        title subject
    }

    body {
        div(style: styles.summary) {
            span "The "
            strong summary.repository.name
            span " repository was updated on "
            strong summary.ref
            span " with "
            strong "${summary.commits.size()} commit${summary.commits.size() > 1 ? 's' : ''}:"

            ul {
                commits.each { details ->
                    li {
                        b { a href: summary.repository.url + '/commit/' + details.sha, details.sha }
                        p {
                            mkp.yieldUnescaped linkGroovyIssues(details.commit.message)
                        }
                    }
                }
            }
        }

        commits.each { details ->
            div(style: styles.commit) {
                dl(style: styles.commitDetails) {
                    dt { strong "Commit:" }
                    dd {
                        a href: summary.repository.url + '/commit/' + details.sha, details.sha
                    }

                    dt { strong "Message:" }
                    dd {
                        mkp.yieldUnescaped linkGroovyIssues(details.commit.message)
                    }

                    dt { strong "Author:" }
                    dd {
                        a href: "mailto:${details.commit.author.email}", details.commit.author.name
                    }

                    dt { strong "When:" }
                    dd details.commit.author.date
                }

                div {
                    details.files.each { file ->

                        div(style: styles.file) {
                            a(href: file.blob_url) {
                                span style: styles.filename, file.filename
                            }
                            span " - (${file.status})"
                        }

                        div(style: styles.patch) {
                            file.patch.eachLine { String line ->
                                if (line.startsWith('@@')) {
                                    span style: styles.diffPosition, line
                                } else if (line.startsWith('+')) {
                                    span style: styles.diffAddition, line
                                } else if (line.startsWith('-')) {
                                    span style: styles.diffDeletion, line
                                } else {
                                    span style: styles.diffNormal, line
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

log.info "HTML generated"

mail.send from: "git@${app.id}.appspotmail.com",
    to: [
        'glaforge@gmail.com', 
        'paulk@asert.com.au', 
        'blackdrag@gmx.org', 
        'cedric.champeau@gmail.com'
    ],
    htmlBody: stringWriter.toString(),
    subject: subject

log.info "Email sent"


